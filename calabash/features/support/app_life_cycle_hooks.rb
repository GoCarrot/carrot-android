require 'calabash-android/management/adb'
require 'calabash-android/operations'

Before do |scenario|
  exec_adb("logcat -c")
  start_test_server_in_background
end

After do |scenario|
  if scenario.failed?
    screenshot_embed
  end
  shutdown_test_server
  exec_adb("shell am force-stop #{package_name(default_device.app_path)}")
end
