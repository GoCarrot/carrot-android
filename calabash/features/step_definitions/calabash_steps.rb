require 'calabash-android/calabash_steps'
require 'insult_generator'
require 'digest/sha1'

###
# https://gist.github.com/sleekweasel/f4f0ef527f83a8aa74ac
When(/^I click a notification with "([^"]*?)"(?: and "([^"]*)")?(?: with (\d+) traversals?)?$/) do |text1, text2, traversals|
  click_notification_matched_by_full_text(traversals, text1, text2)
end

And(/^I verify no notifications? with "([^"]*)"(?: and "([^"]*)")?(?: with (\d+) traversals?)?$/) do |text1, text2, traversals|
  dismiss_notification_matched_by_full_text(traversals, text1, text2)
end
###

When(/^I press the home button$/) do
  exec_adb('shell input keyevent KEYCODE_HOME')
end

When(/^I (?:re)?launch the app(?: again)?$/) do
  exec_adb("shell monkey -p #{package_name(default_device.app_path)} -c android.intent.category.LAUNCHER 1")
end

Then(/^the forground activity should be "([^"]*?)"$/) do |pkg|
  foreground_should_be(pkg)
end

Then(/^my app (?:is|should be) in the foreground$/) do
  foreground_should_be(package_name(default_device.app_path))
end

Then(/^I should be on the home screen$/) do
  foreground_should_be("com.sec.android.app.launcher")
end

Then(/^I insult Android$/) do
  puts InsultGenerator.random_insult
end

Then(/^the Teak state should be "([^"]*?)"$/) do |state|
  current_state = get_teak_run_history.current_state
  fail "Current state is #{current_state}." unless current_state == state
end

Then(/^the Teak Session state should be "([^"]*?)"$/) do |state|
  current_state = get_teak_run_history.current_session.current_state
  fail "Current state is #{current_state}." unless current_state == state
end

Then(/^the Teak Session state should have transitioned from "([^"]*?)"$/) do |state|
  other_state = get_teak_run_history.current_session.state_transitions.last.first
  fail "Current state transitioned from #{other_state}." unless other_state == state
end

Given(/^the Teak Session timeout is (\d+) seconds$/) do |value|
  backdoor "integrationTestTimeout", (value.to_i * 1000).to_s
end

Then(/^I wait for the Teak Session state to be "([^"]*?)"$/) do |state|
  wait_for() do
    get_teak_run_history.current_session.current_state == state
  end
end

Then(/^I (?:want|schedule) a notification that says "([^"]*?)" in (\d+) seconds$/) do |message, delay|
  backdoor "scheduleTestNotification", ["calabash_#{Digest::SHA1.hexdigest(message)}", message, delay]
end

Then(/^the current Teak session attribution should have "([^"]*?)"$/) do |value|
  json_blob = get_teak_run_history.current_session.attribution_payload
  fail "Attribution payload for current session is nil" unless json_blob != nil
  puts json_blob[value] if json_blob[value]
  fail "#{value} not found in #{json_blob}" unless json_blob[value]
end

Then(/^the OS kills my app$/)do
  exec_adb("shell am kill #{package_name(default_device.app_path)}")

  # Check if app is still running
  stdout, stderr, status = exec_adb("shell ps | grep #{package_name(default_device.app_path)}")
  puts "App is still running, background kill simulation only works for test apps running in their own process." unless stdout.empty?
end
