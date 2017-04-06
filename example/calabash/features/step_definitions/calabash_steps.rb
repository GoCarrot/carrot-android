require 'calabash-android/calabash_steps'
require 'insult_generator'

# https://gist.github.com/sleekweasel/f4f0ef527f83a8aa74ac
When(/^I click a notification with "([^"]*?)"(?: and "([^"]*)")?(?: with (\d+) traversals?)?$/) do |text1, text2, traversals|
  click_notification_matched_by_full_text(traversals, text1, text2)
end

And(/^I verify no notifications? with "([^"]*)"(?: and "([^"]*)")?(?: with (\d+) traversals?)?$/) do |text1, text2, traversals|
  dismiss_notification_matched_by_full_text(traversals, text1, text2)
end

Then(/^I insult Android$/) do
  puts InsultGenerator.random_insult
  exec_adb('shell input keyevent KEYCODE_HOME')
end
