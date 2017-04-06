def get_current_teak_state
  stdout, stderr, status = exec_adb('logcat -d -s "Teak"')
  stdout.scan(/Teak State transition from ([A-Za-z]*) -> ([A-Za-z]*)/).last.last
end

def get_current_teak_session_state
  stdout, stderr, status = exec_adb('logcat -d -s "Teak.Session"')
  stdout.scan(/Session State transition from ([A-Za-z]*) -> ([A-Za-z]*)/).last.last
end
