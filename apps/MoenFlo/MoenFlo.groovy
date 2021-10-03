definition(
    name: "Moen Flo",
    namespace: "dacmanj",
    author: "David Manuel",
    description: "Moen Flo Device Manager",
    category: "General",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: "",
    singleInstance: true
)

preferences {
  page(name: "mainPage")
  page(name: "deviceInstaller")
}

import groovy.transform.Field
@Field final String childNamespace = "dacmanj" // namespace of child device drivers
@Field final Map driverMap = [
   "flo_device_v2":       "Moen Flo Shutoff Valve",
   "DEFAULT":             "Moen Flo Shutoff Valve"
]

@Field final String baseUrl = 'https://api-gw.meetflo.com/api/v2'
@Field final String authUrl = 'https://api.meetflo.com/api/v1/users/auth'

def mainPage() {
  if (!state.authenticated) {
    authenticate()
    return deviceInstaller()
  } else {
    return loginPage()
  }
}

def loginPage() {
    if (state.authenticated) {
        return deviceInstaller()
    }
    dynamicPage(name: "mainPage", title: "Manage Your Moen Flo Devices", install: true, uninstall: true) {
        section("<b>Credentials<b>") {
            preferences {        
              input(name: "username", type: "string", title:"User Name", description: "User Name", required: true, displayDuringSetup: true)
              input(name: "password", type: "password", title:"Password", description: "Password", displayDuringSetup: true)
              input(name: "btnLogin", type: "button", title: "Login")
            }
        }
    }
}

def installed() {
  log.debug "installed"
  initialize()
}

def updated() {
  log.debug "updated"
  initialize()
}

def initialize() {
  log.debug "initialize"
  authenticate()
  if (state.token) {
    log.debug("login succeeded")
    getUserInfo()
    discoverDevices()
  }
  unschedule()
  //runEvery5Minutes(checkDevices)
}

def logout() {
    state.token = null
    state.authenticated = false
    state.authenticationFailures = 0
    state.devicesCache = null
    state.locationsCache = null
    state.userData = null
    log.debug "logout()"
    loginPage()
}

def uninstalled() {
  log.debug "uninstalled"
}

def checkDevices() {
  log.debug "checkDevices"
}

def deviceInstaller() {
    if (!state.authenticated) {
        return loginPage()
    }

    dynamicPage(name: "deviceInstaller", title: "", nextPage: null, uninstall: true) {
        section("<b>Installed Devices</b>") {
            app(name: "anyOpenApp", appName: "Moen Flo Instance", namespace: "dacmanj", title: "<b>Add a new device</b>", multiple: true)
        }
        section("<b>Settings</b>") {
            input(name: 'logEnable', type: "bool", title: "Enable Debug Logging?", required: false, defaultValue: false, submitOnChange: true)
            input(name: "btnLogout", type: "button", title: "Logout")
        }
    }
}

def getDriverMap() {
    return driverMap;
}

def getIdsOfInstalledDevices() {
    return getChildDevices().collect{ dev ->
        return dev.deviceNetworkId
    }
}

def authenticate() {
    if (logEnable) log.debug("authenticate()")
    if (logEnable) log.debug("failure count: ${state.authenticationFailures}")
    def uri = authUrl
    if (!password) {
        log.error("Login Failed: No password")
    }
    else {
        if (authenticationFailures > 3) {
            log.error("Failed to authenticate after three tries. Giving up. Log out and back in to retry.")
        }
        
        def body = [username:username, password:password]
        def headers = [:]
        headers.put("Content-Type", "application/json")

        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { response -> def msg = response?.status
                if (logEnable) log.debug("Login received response code ${response?.status}")
                    if (response?.status == 200) {
                        msg = "Success"
                        state.token = response.data.token
                        state.userId = response.data.tokenPayload.user.user_id
                        state.authenticated = true
                        state.authenticationFailures = 0
                    }
                    else {
                        log.error "Login Failed: (${response.status}) ${response.data}"
                        state.authenticated = false
                        state.authentcationFailures += 1
                    }
              }
        }
        catch (Exception e) {
            log.error "Login exception: ${e}"
            log.error "Login Failed: Please confirm your Flo Credentials"
            state.authenticated = false
            state.authentcationFailures += 1
        }
    }
}

def getUserInfo() {
  def userId = state.userId
  def uri = "${baseUrl}/users/${userId}?expand=locations,alarmSettings"
  def response = makeAPIGet(uri, "Get User Info")
  state.userData = response.data
}

def discoverDevices() {
  def locations = []
  Map devicesCache = [:]
  Map locationsCache = [:]
  def userLocations = state.userData?.locations
  if(userLocations) {
    userLocations.each { location ->
      def locationDetail = getLocationData(location.id)
      def devices = locationDetail?.devices
      if (devices) {
        locations.add(locationDetail)
        devices.each{ d ->
          devicesCache << [(d.id): (d)]
        }
        locationsCache << [(locationDetail.id): (locationDetail)]
      }
    }
  } else {
    if (logEnable) log.debug "No locations in user data"
  }
  state.userData?.locations = locations
  state.devicesCache = devicesCache
  state.locationsCache = locationsCache
}

def getLocationData(locationId) {
  def uri = "${baseUrl}/locations/${locationId}?expand=devices"
  def response = makeAPIGet(uri, "Get Location Info")
  return response.data
}

def getDeviceData(deviceId) {
  def uri = "${baseUrl}/devices/${deviceId}"
  def response = makeAPIGet(uri, "Get Device")
  return response.data
}

def makeAPIGet(uri, request_type, success_status = [200, 202]) {
    if (logEnable) log.debug "makeAPIGet: ${request_type} ${uri}"
    def token = state.token
    if (!token || token == "") authenticate();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:]
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", token)

        try {
            httpGet([headers: headers, uri: uri]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.error "${request_type} Failed (${response.status}): ${response.data}"
                }
              }
        }
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage()?.contains("Forbidden") || e.getMessage()?.contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception..."
            } else {
                log.error "${request_type} Failed ${e}"
            }
            state.token = null
            authenticate()

        }
        tries++

    }
    return response
}

def makeAPIPost(uri, body, request_type, success_status = [200, 202]) {
    if (logEnable) log.debug "makeAPIGet: ${request_type} ${uri}"
    def token = state.token
    if (!token || token == "") authenticate();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:]
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", token)

        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.debug "${request_type} Failed (${resp.status}): ${resp.data}"
                }
            }
        }
        catch (Exception e) {
            log.error "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Forbidden/Unauthorized Exception... Refreshing token..."
                authenticate()
            }
        }
        tries++

    }
    return response
}


void appButtonHandler(btn) {
   switch(btn) {
      case "btnLogout":
         logout()
         break
      case "btnLogin":
         authenticate()
         deviceInstaller()
         break
      case "btnLocationRefresh":
         discoverDevices()
         break
      case "btnInstallDevice":
         createNewSelectedDevices()
         break
      default:
         log.warn "Unhandled app button press: $btn"
   }
}
