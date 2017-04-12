require_relative 'teak_run_history'

def get_teak_run_history
  stdout, stderr, status = exec_adb('logcat -d -s "Teak" "Teak.Session"')
  TeakRunHistory.new(stdout)
end

def get_teak_request_or_reply_json(request_or_reply, type)
  stdout, stderr, status = exec_adb("logcat -d -s \"Teak.#{request_or_reply == :request ? "Request" : "Reply"}\"")
  request_or_reply_string = :request ? "Submitting request to" : "Reply from"
  regexp = case type
    when :users, :settings
      /#{request_or_reply_string} \'\/games\/(?:\d+)\/#{type}\.json\'\: (.*)/
    else
      /#{request_or_reply_string} \'\/me\/#{type}\.json\'\: (.*)/
  end
  stdout.scan(regexp).map{ |x| JSON.parse(x.first) }
end

def forground_activity_package_name
  stdout, stderr, status = exec_adb('shell dumpsys activity | grep top-activity | cut -d ":" -f 4- | cut -d "/" -f 1')
  stdout.strip
end

def foreground_should_be(pkg)
  fg_pkg = forground_activity_package_name
  fail "Foreground activity is #{fg_pkg}" unless fg_pkg.start_with? pkg
end
