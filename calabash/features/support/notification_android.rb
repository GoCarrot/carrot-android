# https://gist.github.com/sleekweasel/f4f0ef527f83a8aa74ac
require 'nokogiri'
require_relative 'bounds'

def exec_adb(cmd)
  adb_cmd = "#{default_device.adb_command} #{cmd}"
  stdout, stderr, status = Open3.capture3(adb_cmd)
  fail "Adb failed: #{adb_cmd} Returned #{stdout} :: #{stderr} :: #{status}" unless status.success?
  [stdout, stderr, status]
end

def uiautomator_file(stdout)
  /dumped to: (?<file>\S*)/ =~ stdout
  file
end

def uiautomator_dump
  stdout, stderr, status = exec_adb('shell uiautomator dump')
  file = uiautomator_file(stdout)
  fail "uiautomator dump failed? Returned #{stdout} :: #{stderr} :: #{status}" unless file
  stdout, stderr, status = exec_adb("shell cat #{file}")
  [stdout, stderr, status]
end

# Constants for the easy indexing of bounds elements within the array:
X0 ||= 0
Y0 ||= 1
X1 ||= 2
Y1 ||= 3

def bounds_of_node(node)
  bounds = node.xpath('@bounds').to_s
  match = bounds.empty? ? nil : bounds.match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/)
  Bounds.new(match.captures.collect(&:to_i)) if match
end

# Random heuristic: for the purposes of whether notifications are
# obscured, we ignore things taller than half the parent. Probably
# sufficient to check for simple match, now that's 'half the parent'
# rather than 'half the screen'.
def plausibly_small?(bounds, full)
  return unless bounds && bounds != full
  fail "plausibly_small? was given non-trivial 'bounds'" unless bounds.trivial?
  fail "plausibly_small? was given non-trivial 'full'" unless full.trivial?
  br = bounds.leftmost_rectangle
  fr = full.leftmost_rectangle
  br[Y1] - br[Y0] < (fr[Y1] - fr[Y0]) / 2
end

def highest_ancestor_of_node(node, xml)
  ancestor = node
  ancestor = ancestor.parent while ancestor && ancestor.parent != xml
  fail "#{node.path} is not a child of #{xml.path}?!" unless ancestor
  ancestor
end

def clip_and_unobscure_bounds(ancestor, bounds, root)
  root_bounds = bounds_of_node(root)
  bounds.clip_to_bounds(root_bounds) if bounds && root_bounds
  root.children.each do |child|
    if child.element? && child != ancestor && bounds
      b = bounds_of_node(child)
      bounds.subtract_bounds(b) if plausibly_small?(b, root_bounds)
    end
  end
  bounds
end

def bounds_clipped_and_unobscured(node, root)
  return nil if node.nil?
  if node == root
    return bounds_of_node(node)
  end
  ancestor = highest_ancestor_of_node(node, root)
  bounds = bounds_clipped_and_unobscured(node, ancestor)
  bounds = clip_and_unobscure_bounds(ancestor, bounds, root) if bounds
  bounds
end

# Run block with leftmost rectangle from xpath and xml that is not
# covered by non-ancestor nodes. Leftmost for swipe-dismiss notification.
def unobscured_rectangle_from_xml(xpath, xml)
  xml_nodes = xml.xpath(xpath)
  return nil unless xml_nodes && !xml_nodes.empty?
  bounds = bounds_clipped_and_unobscured(xml_nodes.first, xml)
  return nil unless bounds
  yield bounds.leftmost_rectangle
end

def uiautomator_xml
  stdout, _stderr, _status = uiautomator_dump
  Nokogiri::XML(stdout)
end

# INFO: Unfortunately, the UIDevice.openNotification() method causes
# an 'exec error' on the Samsung GT-N7100, so we're using this
# workaround instead of the new uiautomator.apk
def open_notification_shutter(xml)
  puts 'Finding shutter'
  unobscured_rectangle_from_xml('(//node)[1]', xml) do |x1, y1, x2, y2|
    xm = (x1 + x2) / 2
    puts 'Opening shutter'
    exec_adb("shell input swipe #{xm} #{y1} #{xm} #{y2}")
  end
end

# Returns true if notification clicked, otherwise repeats
def tap_notification(xpath, xml)
  found_bounds = unobscured_rectangle_from_xml(xpath, xml) do |x1, y1, x2, y2|
    exec_adb("shell input tap #{(x1 + x2) / 2} #{(y1 + y2) / 2}")
  end
  dismissed = !found_bounds.nil?
  { stop: dismissed,
    dismissed: dismissed,
    fail: dismissed ? nil : 'Could not see notification' }
end

# Never fails, never stops.
def dismiss_notification(xpath, xml)
  unobscured_rectangle_from_xml(xpath, xml) do |x1, y1, _x2, y2|
    ym = (y1 + y2) / 2
    exec_adb("shell input swipe #{x1} #{ym} 10000 #{ym}")
  end
  { stop: false }
end

# Returns grandparent of grandchild nodes with given text.
def xpath_for_full_path_texts(params)
  texts = params.keys.grep(/^notification.full./)
  clause = texts.collect do |k|
    params[k].split('|').collect { |s| "./node/node[@text='#{s}']" }.join(' or ')
  end.join('][')
  "//node[#{clause}]"
end

def pull_notifications(updown, times, xml)
  unobscured_rectangle_from_xml('(//node)[1]', xml) do |x1, y1, x2, y2|
    xm = x1 + (x2 - x1) / 2
    yf = y1 + (y2 - y1) * 3 / 4
    yt = yf + (updown == :up ? -1 : 1) * 100 # (y2 - y1) / 8
    begin
      puts("shell '" + (" input swipe #{xm} #{yf} #{xm} #{yt};" * times) + "'")
      exec_adb("shell '" + (" input swipe #{xm} #{yf} #{xm} #{yt};" * times) + "'")
      {}
    rescue StandardError => e
      { stop: true, fail: e.message }
    end
  end
end

def interact_with_notification(params, xml, xpath)
  if params['action.click']
    return tap_notification(xpath, xml)
  else
    return dismiss_notification(xpath, xml)
  end
end

def seek_notification(params, xpath)
  open_notification_shutter(uiautomator_xml)
  xml = uiautomator_xml
  result = {}
  time_out = Time.now + 120 # Emergency 2 min timeout
  params['traversals'].to_i.times do
    # Scroll quickly to top, then slowly to the bottom
    [[:down, 6], [:up, 2]].each do |direction, steps|
      begin
        return { fail: 'Timed out' } if Time.now > time_out
        result = interact_with_notification(params, xml, xpath)
        return result if result[:stop]
        pull = pull_notifications(direction, steps, xml)
        return pull if pull[:stop]
        last_xml = xml
        xml = uiautomator_xml
      end until xml.to_s == last_xml.to_s
    end
  end
  result
end

# 1. Phones like Galaxy 3 remember where the notifications pull-down was scrolled to,
#     so we need to drag down repeatedly until we observe no change (i.e. top notification is visible).
# 2. Phones like MotoG 5.0  have a two-part pull-down: first swipe shows notifications and
#     second shows a settings panel with a tiny notifications at the bottom,
#     so pulling down needs to be done in small steps
# 3. Phones like Galaxy 5 close the pull-down if the lowest notification is pulled higher than half the screen,
#     so we don't do that.

def handle_notification(params)
  xpath = xpath_for_full_path_texts(params)
  result = seek_notification(params, xpath)
  press_back_button unless result[:dismissed]
  fail result[:fail] if result[:fail]
end

def click_notification_matched_by_full_text(traversals, *strings)
  traversals ||= '1'
  hash = { 'traversals' => traversals, 'action.click' => 'true' }
  strings.map.with_index { |v, ix| hash["notification.full.#{ix}"] = v if v }
  handle_notification(hash)
end

def dismiss_notification_matched_by_full_text(traversals, *strings)
  traversals ||= '1'
  hash = { 'traversals' => traversals, 'action.dismiss' => 'true' }
  strings.map.with_index { |v, ix| hash["notification.full.#{ix}"] = v if v }
  handle_notification(hash)
end
