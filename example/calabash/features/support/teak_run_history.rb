class TeakRunHistory
  attr_reader :sdk_version, :app_configuration, :device_configuration,
    :state_transitions, :lifecycle_events, :sessions

  def initialize(stdout)
    @sdk_version = nil
    @app_configuration = nil
    @device_configuration = nil
    @app_configurations = {}
    @device_configurations = {}
    @state_transitions = [[nil, "Allocated"]]
    @lifecycle_events = []
    @sessions = []

    stdout.each_line do |line|
      new_log_line(line)
    end
  end

  def current_state
    @state_transitions.last.last
  end

  def current_session
    @sessions.last
  end

  def new_log_line(line)
    case line
    # Teak
    when /([A-Z]) Teak(?:\s*)\: (.*)/ # $1 is D/W/E/V, $2 is the event
      event = $2
      case event
      when /^io\.teak\.sdk\.Teak@([a-fA-F0-9]+)\: (.*)/
        @id = $1
        json = JSON.parse($2)
        @sdk_version = json["android"]
      when /^State@([a-fA-F0-9]+)\: (.*)/
        raise "Teak got re-created #{@id} -> #{$1}" unless $1 == @id
        on_new_state(JSON.parse($2))
      when /^Lifecycle@([a-fA-F0-9]+)\: (.*)/
        raise "Teak got re-created #{@id} -> #{$1}" unless $1 == @id
        on_new_lifecycle(JSON.parse($2))
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
      else
        puts "Unrecognized Teak event: #{event}"
      end

    # Teak.Session
    when /([A-Z]) Teak.Session(?:\s*)\: (.*)/ # $1 is D/W/E/V, $2 is the event
      session = Session.new_event($2, current_session)
      if current_session == nil or session.id != current_session.id
        raise "#{event}\nDuplicate session created" unless @sessions.find_all { |s| s.id == session.id }.empty?
        @sessions << session
      end

    # --------- beginning of main/system or all whitespace
    when /^-/, /^\s*$/
      # Ignore
    else
        puts "Unrecognized log line: #{line}"
    end
  end

  def on_new_state(json)
    raise "State transition consistency failed, current state is '#{current_state}', expected '#{json["previousState"]}'" unless current_state == json["previousState"]
    @state_transitions << [json["previousState"], json["state"]]
  end

  def on_new_lifecycle(json)
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
    attr_reader :id, :state_transitions, :user_id, :heartbeats

    def initialize(id, start_date)
      @id = id
      @user_id = nil
      @start_date = start_date
      @state_transitions = [[nil, "Allocated"]]
      @heartbeats = []
    end

    def current_state
      @state_transitions.last.last
    end

    def self.new_event(event, current_session)
      case event
      when /^io.teak.sdk.Session@([a-fA-F0-9]+)\: (.*)/
        json = JSON.parse($2)
        current_session = self.new($1, DateTime.strptime(json["startDate"].to_s,'%s'))
      when /^State@([a-fA-F0-9]+)\: (.*)/
        raise "State transition for nil session" unless current_session != nil
        raise "State transition for non-current session" unless current_session.id == $1
        current_session.on_new_state(JSON.parse($2))
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
      current_session
    end

    def on_new_state(json)
      raise "State transition consistency failed" unless current_state == json["previousState"]
      @state_transitions << [json["previousState"], json["state"]]
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
end
