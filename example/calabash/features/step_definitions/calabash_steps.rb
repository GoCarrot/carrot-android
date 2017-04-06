require 'calabash-android/calabash_steps'
require 'insult_generator'

# https://gist.github.com/sleekweasel/f4f0ef527f83a8aa74ac
When(/^I click a notification with "([^"]*?)"(?: and "([^"]*)")?(?: with (\d+) traversals?)?$/) do |text1, text2, traversals|
  click_notification_matched_by_full_text(traversals, text1, text2)
end

And(/^I verify no notifications? with "([^"]*)"(?: and "([^"]*)")?(?: with (\d+) traversals?)?$/) do |text1, text2, traversals|
  dismiss_notification_matched_by_full_text(traversals, text1, text2)
end

When(/^I press the home button$/) do
  exec_adb('shell input keyevent KEYCODE_HOME')
end

When(/^I (?:re)?launch the app(?: again)?$/) do
  exec_adb("shell monkey -p #{package_name(default_device.app_path)} -c android.intent.category.LAUNCHER 1")
end

Then(/^I insult Android$/) do
  puts InsultGenerator.random_insult
end

Then(/^I fast-forward (\d+) seconds$/) do |seconds|
  puts "Fast forwarding #{seconds} seconds!"
end

Then(/^the Teak state should be "([^"]*?)"$/) do |state|
  current_state = get_current_teak_state
  fail "Current state is #{current_state}." unless current_state == state
end

Then(/^the Teak Session state should be "([^"]*?)"$/) do |state|
  current_state = get_current_teak_session_state
  fail "Current state is #{current_state}." unless current_state == state
end
