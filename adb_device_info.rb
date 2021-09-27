# frozen_string_literal: true

device_ids = []
`adb devices`.each_line.with_index do |line, index|
  next if index.zero?

  line.chomp!
  next if line.empty?

  device_ids << line.split.first
end

device_ids.each do |device_id|
  android_version = `adb -s #{device_id} shell getprop ro.build.version.release`.chomp
  device_model = `adb -s #{device_id} shell getprop ro.product.model`.chomp
  device_manufacturer = `adb -s #{device_id} shell getprop ro.product.manufacturer`.chomp
  display_dpi = `adb -s #{device_id} shell wm density`.split.last.chomp
  display_resolution = `adb -s #{device_id} shell wm size`.split.last.chomp
  memory_mb = '%.2f' % (`adb -s #{device_id} shell cat /proc/meminfo | grep MemTotal`.split[1].to_f / 1024)
  # puts `adb -s #{device_id} shell cat /sys/class/power_supply/battery/cycle_count`
  # puts `adb -s #{device_id} shell cat /sys/class/power_supply/battery/charge_full`
  puts [device_id, android_version, device_model, device_manufacturer, display_dpi, display_resolution, memory_mb].join ','
end
