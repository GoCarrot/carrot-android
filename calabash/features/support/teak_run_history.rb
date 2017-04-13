require 'date'
require 'json'

class TeakRunHistory
  attr_reader :sdk_version, :app_configuration, :device_configuration,
    :state_transitions, :lifecycle_events, :sessions

  def initialize
    @sdk_version = nil
    @app_configuration = nil
    @device_configuration = nil
    @app_configurations = {}
    @device_configurations = {}
    @state_transitions = [[nil, "Allocated"]]
    @lifecycle_events = []
    @sessions = []
  end

  def read_lines(lines, event_stream = EventStream.new)
    lines.each_line do |line|
      event_stream = new_log_line(line, event_stream)
    end
    event_stream
  end

  def current_state
    @state_transitions.last.last
  end

  def current_session
    @sessions.last
  end

  def new_log_line(line, event_stream = EventStream.new)
    case line
    # Teak
    when /([A-Z]) Teak(?:\s*)\: (.*)/ # $1 is D/W/E/V, $2 is the event
      event = $2
      case event
      when /^io\.teak\.sdk\.Teak@([a-fA-F0-9]+)\: (.*)/
        @id = $1
        json = JSON.parse($2)
        @sdk_version = json["android"]
        event_stream.emit({component: :teak, action: :create})
      when /^State@([a-fA-F0-9]+)\: (.*)/
        raise "Teak got re-created #{@id} -> #{$1}" unless $1 == @id
        event_stream = on_new_state(JSON.parse($2), event_stream)
      when /^Lifecycle@([a-fA-F0-9]+)\: (.*)/
        raise "Teak got re-created #{@id} -> #{$1}" unless $1 == @id
        event_stream = on_new_lifecycle(JSON.parse($2), event_stream)
      when /^io\.teak\.sdk\.AppConfiguration@([a-fA-F0-9]+)\: (.*)/
        raise "Duplicate app configuration created" unless not @app_configurations.has_key?($1)
        @app_configurations[$1] = JSON.parse($2)
      when /^io\.teak\.sdk\.DeviceConfiguration@([a-fA-F0-9]+)\: (.*)/
        raise "Duplicate device configuration created" unless not @device_configurations.has_key?($1)
        @device_configurations[$1] = JSON.parse($2)
      when /^io\.teak\.sdk\.RemoteConfiguration@([a-fA-F0-9]+)\: (.*)/
        # io.teak.sdk.RemoteConfiguration@2c5e229: {"sdkSentryDsn":"https:\/\/e6c3532197014a0583871ac4464c352b:41adc48a749944b180e88afdc6b5932c@sentry.io\/141792","appSentryDsn":null,"hostname":"gocarrot.com"}
      when /^IdentifyUser@([a-fA-F0-9]+)\: (.*)/
        # IdentifyUser@36c6cad: {"userId": "demo-app-thingy-3"}
      when /^Notification@([a-fA-F0-9]+)\: (.*)/
        # Notification@1e480ea: {"teakNotifId": "852321299714932736", "autoLaunch"=true}
      else
        puts "Unrecognized Teak event: #{event}"
      end

    # Teak.Session
    when /([A-Z]) Teak.Session(?:\s*)\: (.*)/ # $1 is D/W/E/V, $2 is the event
      session, event_stream = Session.new_event($2, current_session, @sessions, event_stream)
      if current_session == nil or session.id != current_session.id
        raise "#{event}\nDuplicate session created" unless @sessions.find_all { |s| s.id == session.id }.empty?
        @sessions << session
      end

    # Teak.Request
    when /([A-Z]) Teak.Request(?:\s*)\: (.*)/ # $1 is D/W/E/V, $2 is the event
      event = $2
      case event
      when /^Request@([a-fA-F0-9]+)\: (.*)/
        json = JSON.parse($2)
        session = @sessions.find { |s| s.id == json["session"] }
        raise "Session #{$1} not found" unless session != nil
        event_stream = session.attach_request($1, json, event_stream)
      when /^Reply@([a-fA-F0-9]+)\: (.*)/
        json = JSON.parse($2)
        session = @sessions.find { |s| s.id == json["session"] }
        raise "Session #{$1} not found" unless session != nil
        event_stream = session.attach_reply($1, json, event_stream)
      else
        puts "Unrecognized Teak.Request event: #{event}"
      end

    # --------- beginning of main/system or all whitespace
    when /^-/, /^\s*$/
      # Ignore
    else
        #puts "Unrecognized log line: #{line}"
    end
    event_stream
  end

  def on_new_state(json, event_stream)
    raise "State transition consistency failed, current state is '#{current_state}', expected '#{json["previousState"]}'" unless current_state == json["previousState"]
    @state_transitions << [json["previousState"], json["state"]]
    event_stream
  end

  def on_new_lifecycle(json, event_stream)
    case json["callback"]
      when "onActivityCreated"
        raise "Unknown device configuration" unless @device_configurations.has_key?(json["deviceConfiguration"])
        raise "Device configuration already assigned" unless @device_configuration == nil
        @device_configuration = @device_configurations[json["deviceConfiguration"]]
        raise "Unknown app configuration" unless @app_configurations.has_key?(json["appConfiguration"])
        raise "App configuration already assigned" unless @app_configuration == nil
        @app_configuration = @app_configurations[json["appConfiguration"]]

        # The rest of the json info is available in the app/device configuration
        json = {"callback": "onActivityCreated"}
    end
    @lifecycle_events << json
    event_stream
  end

  def to_h
    {
      id: @id,
      sdk_version: @sdk_version,
      current_state: current_state,
      state_transitions: @state_transitions,
      app_configuration: @app_configuration,
      device_configuration: @device_configuration,
      lifecycle_events: @lifecycle_events,
      current_session: current_session.to_h
    }
  end

  class Session
    attr_reader :id, :state_transitions, :user_id, :heartbeats, :requests, :attribution_payload

    def initialize(id, start_date)
      @id = id
      @user_id = nil
      @start_date = start_date
      @state_transitions = [[nil, "Allocated"]]
      @heartbeats = []
      @requests = {}
      @attribution_payload = nil
    end

    def current_state
      @state_transitions.last.last
    end

    def attach_request(id, json, event_stream)
      raise "Duplicate request created #{id}" unless not @requests.has_key?(id)
      @requests[id] = {
        request: json,
        reply: nil
      }

      if json["endpoint"].match(/\/games\/(?:\d+)\/users\.json/)
        if @attribution_payload == nil
          raise "Attribution payload contains 'do_not_track_event'" unless not json["payload"].has_key?("do_not_track_event")
          @attribution_payload = json["payload"]
        else
          raise "Additional payload does not specify 'do_not_track_event'" unless json["payload"].has_key?("do_not_track_event")
        end
      end
      event_stream
    end

    def attach_reply(id, json, event_stream)
      raise "Reply for non-existent request #{id}" unless @requests.has_key?(id)
      raise "Duplicate reply created #{id}" unless @requests[id][:reply] == nil
      @requests[id][:reply] = json
      event_stream
    end

    def self.new_event(event, current_session, sessions, event_stream)
      case event
      when /^io.teak.sdk.Session@([a-fA-F0-9]+)\: (.*)/
        json = JSON.parse($2)
        current_session = self.new($1, DateTime.strptime(json["startDate"].to_s,'%s'))
      when /^State@([a-fA-F0-9]+)\: (.*)/
        session = sessions.find { |s| s.id == $1 }
        raise "State transition for non-existent session" unless session != nil
        event_stream = session.on_new_state(JSON.parse($2), event_stream)
      when /^Heartbeat@([a-fA-F0-9]+)\: (.*)/
        raise "Heartbeat for nil session" unless current_session != nil
        raise "Heartbeat for non-current session" unless current_session.id == $1
        json = JSON.parse($2)
        #raise "Heartbeat for different userId than current" unless current_session.user_id == json["userId"]
        current_session.heartbeats << DateTime.strptime(json["timestamp"].to_s,'%s')
      when /^IdentifyUser@([a-fA-F0-9]+)\: (.*)/
        #IdentifyUser@ddff9ae: {"userId":"demo-app-thingy-3","timezone":"-7.00","locale":"en_US"}
      else
        puts "Unrecognized Teak.Session event: #{event}"
      end
      [current_session, event_stream]
    end

    def on_new_state(json, event_stream)
      raise "State transition consistency failed" unless current_state == json["previousState"]
      @state_transitions << [json["previousState"], json["state"]]
      event_stream
    end

    def to_h
      {
        id: @id,
        current_state: current_state,
        state_transitions: @state_transitions,
        user_id: @user_id,
        start_date: @start_date
      }
    end
  end

  class EventStream
    attr_reader :events

    def initialize
      @events = []
    end

    def emit(event)
      @events << event
    end

    def to_a
      @events
    end

    def human_readable
      return nil if @events.empty?
      JSON.pretty_generate(@events)
    end

    class Event
      def initialize
      end
    end
  end
end
