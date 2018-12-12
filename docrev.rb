#!/usr/bin/env ruby

sections = ['Breaking Changes', 'New Features', 'Bug Fixes']

lines = []
major, minor, patch = nil
ARGF.each do |line|
  if (match = line.match(/^(\d+\.)?(\d+\.)?(\*|\d+)$/))
    major, minor, patch = match.captures.map(&:to_i)
  elsif sections.include? line.strip
    lines << ":#{line.strip}:"
  elsif line.start_with? 'Native SDK Versions'
    break
  else
    lines << "    * #{line.strip}" unless line.strip.empty?
  end
end

if major.nil? && minor.nil? && patch.nil?
  STDERR.puts 'Could not find version number in input.'
  return
end

File.open("docs/versions/#{major}.#{minor}.#{patch}.rst", 'w') do |file|
  version_str = "#{major}.#{minor}.#{patch}"
  file.write <<~TEMPLATE
    .. include:: ../changelog_entry.rst

    #{version_str}
    #{'-' * version_str.length}
    #{lines.join("\n")}
  TEMPLATE
end
