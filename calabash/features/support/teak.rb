require_relative 'teak_run_history'

def get_teak_run_history
  stdout, stderr, status = exec_adb('logcat -d -s "Teak:D" "Teak.Session:D" "Teak.Request:D"')
  TeakRunHistory.new(stdout)
end

def forground_activity_package_name
  stdout, stderr, status = exec_adb('shell dumpsys activity | grep top-activity | cut -d ":" -f 4- | cut -d "/" -f 1')
  stdout.strip
end

def foreground_should_be(pkg)
  fg_pkg = forground_activity_package_name
  fail "Foreground activity is #{fg_pkg}" unless fg_pkg.start_with? pkg
end
