def get_current_teak_state
  stdout, stderr, status = exec_adb('logcat -d -s "Teak"')
  stdout.scan(/Teak State transition from ([A-Za-z]*) -> ([A-Za-z]*)/).last.last
end

def get_all_teak_session_state_transitions
  stdout, stderr, status = exec_adb('logcat -d -s "Teak.Session"')
  stdout.scan(/Session State transition from ([A-Za-z]*) -> ([A-Za-z]*)/)
end

def get_current_teak_session_state
  get_all_teak_session_state_transitions.last
end

def get_teak_session_transitions
  ret = []
  current = []
  get_all_teak_session_state_transitions.each do |transition|
    current.push transition
    if transition.last == "Expired" then
      ret.push current
      current = []
    end
  end
  ret.push current if not current.empty?
  ret
end

def forground_activity_package_name
  stdout, stderr, status = exec_adb('shell dumpsys activity | grep top-activity | cut -d ":" -f 4- | cut -d "/" -f 1')
  stdout.strip
end

def foreground_should_be(pkg)
  fg_pkg = forground_activity_package_name
  fail "Foreground activity is #{fg_pkg}" unless pkg.casecmp(fg_pkg) == 0
end
