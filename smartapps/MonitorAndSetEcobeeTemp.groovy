/**
 *  MonitorAndSetEcobeeTemp
 *
 *  Copyright 2014 Yves Racine
 *  linkedIn profile: ca.linkedin.com/pub/yves-racine-m-sc-a/0/406/4b/
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable lax or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * The MonitorAndSetEcobeeTemp monitors the outdoor temp and adjusts the heating and cooling set points
 * at regular intervals (input parameter in minutes) according to heat/cool thresholds that you set (input parameters).
 * It also constantly monitors any 'holds' at the thermostat to make sure that these holds are justified according to
 * the motion sensors at home and the given thresholds.
 */
definition(
	name: "MonitorAndSetEcobeeTemp",
	namespace: "yracine",
	author: "Yves Racine",
	description: "Monitor And Set Ecobee Temperature according to indoor motion sensors & outdoor temperature and humidity.",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {

	page(name: "thresholdSettings", title: "ThresholdSettings")
	page(name: "sensorSettings", title: "SensorSettings")
	page(name: "otherSettings", title: "OtherSettings")
}


def thresholdSettings() {
	dynamicPage(name: "thresholdSettings", install: false, uninstall: true, nextPage: "sensorSettings") {
		section("About") {	
			paragraph "MonitorAndSetEcobeeTemp, the smartapp that can adjust your ecobee's setpoints based on in/out-door sensors"
			paragraph "Version 1.9\n\n" +
			"If you like this app, please support the developer via PayPal:\n\nyracine@yahoo.com\n\n" +
			"Copyright©2014 Yves Racine"
			href url:"http://github.com/yracine/device-type.myecobee", style:"embedded", required:false, title:"More information...", 
			description: "http://github.com/yracine/device-type.myecobee"
		}
		section("Monitor indoor/outdoor temp & adjust the ecobee thermostat's setpoints") {
			input "ecobee", "capability.thermostat", title: "Which Ecobee?"
		}
		section("For more heating in cold season, outdoor temp's threshold (default <= 10°F/-17°C)") {
			input "givenMoreHeatThreshold", "decimal", title: "Outdoor temp's threshold for more heating", required: false
		}
		section("For less heating in cold season, outdoor temp's threshold (default >= 50°F/10°C)") {
			input "givenLessHeatThreshold", "decimal", title: "Outdoor temp's threshold for less heating", required: false
		}
		section("For more cooling in hot season, outdoor temp's threshold (default >= 85°F/30°C)") {
			input "givenMoreCoolThreshold", "decimal", title: "Outdoor temp's threshold for more cooling", required: false
		}	
		section("For less cooling in hot season, outdoor temp's threshold (default <= 75°F/22°C)") {
			input "givenLessCoolThreshold", "decimal", title: "Outdoor temp's threshold for less cooling", required: false
		}
		section("For more cooling/heating, outdoor humidity's threshold (default >= 85%)") {
			input "givenHumThreshold", "number", title: "Outdoor Relative humidity's threshold for more cooling/heating",
				required: false
		}
		section("At which interval in minutes (range=[10..59],default=59 min.)?") {
			input "givenInterval", "number", title:"Interval", required: false
		}
		section("Maximum Temp adjustment in Farenheits/Celsius") {
			input "givenTempDiff", "decimal", title: "Max Temp adjustment (default= +/-5°F/2°C)", required: false
		}
	}
}
    

def sensorSettings() {
	dynamicPage(name: "sensorSettings", title: "Sensors to be used", install: false, nextPage: otherSettings) {
		section("Choose outdoor Temperature & Humidity sensor to be used for temp adjustment") {
			input "outdoorSensor", "capability.temperatureMeasurement", title: "Outdoor Temperature Sensor"
		}
		section("Choose indoor sensor(s) with both Motion & Temp capabilities to be used for dynamic temp adjustment when occupied (optional)") {
			input "indoorSensors", "capability.motionSensor", title: "Which Indoor Motion/Temperature Sensor(s)", required: false, multiple:true
		}
		section("Choose any other indoor temp sensors for avg temp adjustment (optional)") {
        	input "tempSensors", "capability.temperatureMeasurement", title: "Any other temp sensors?",  multiple: true, required: false
    	}
		section("Choose any other indoor motion sensors for setting climate to [Away, Home] (optional)") {
        	input "motions", "capability.motionSensor", title: "Any other motion sensors?",  multiple: true, required: false
    	}
		section("Trigger climate/temp adjustment when motion or no motion has been detected for (default=15 minutes)") {
        	input "residentsQuietThreshold", "number", title: "Time in minutes", required: false
		}
	}        
}
 
 
def otherSettings() {
	dynamicPage(name: "otherSettings", title: "Other Settings", install: true, uninstall: false) {
    
		section("What do I use for the Master on/off switch to enable/disable processing? (optional)") {
			input "powerSwitch", "capability.switch", required: false
		}
		section("Notifications") {
			input "sendPushMessage", "enum", title: "Send a push notification?", metadata: [values: ["Yes", "No"]], required:
				false
			input "phoneNumber", "phone", title: "Send a text message?", required: false
		}
		section("Detailed Notifications") {
			input "detailedNotif", "Boolean", title: "Detailed Notifications?", metadata: [values: ["true", "false"]], required:
				false
		}
		section([mobileOnly:true]) {
			label title: "Assign a name for this SmartApp", required: false
		}
	}
}



def installed() {
	initialize()
}

def updated() {
	// we have had an update
	// remove everything and reinstall
	unschedule()
    
	unsubscribe()
	initialize()
}
def initialize() {
	log.debug "Initialized with settings: ${settings}"

 	reset_state_program_values()
	reset_state_motions()
	reset_state_tempSensors()
    
	Integer delay = givenInterval ?: 59 // By default, do it every hour
	if ((delay < 10) || (delay>59)) {
		log.error "Scheduling delay not in range (${delay} min), exiting..."
		runIn(30, "sendNotifDelayNotInRange")
 		return
	}
	log.debug "Scheduling ecobee temp Monitoring and adjustment every ${delay}  minutes"

	schedule("0 0/${delay} * * * ?", monitorAdjustTemp) // monitor & set indoor temp according to delay specified


	subscribe(indoorSensors, "motion",motionEvtHandler, [filterEvents: false])
	subscribe(motions, "motion", motionEvtHandler, [filterEvents: false])
    
/*    
	subscribe(ecobee, "heatingSetpoint", ecobeeHeatTempHandler)
	subscribe(ecobee, "coolingSetpoint", ecobeeCoolTempHandler)
	subscribe(ecobee, "humidity", ecobeeHumidityHandler)
	subscribe(ecobee, "temperature", ecobeeTempHandler)
	subscribe(ecobee, "thermostatMode", ecobeeModeHandler)
	subscribe(outdoorSensor, "humidity", outdoorSensorHumHandler)
	subscribe(outdoorSensor, "temperature", outdoorTempHandler)
    
    
	subscribe(ecobee, "programType", ecobeeProgramTypeHandler)
	subscribe(ecobee, "programCoolTemp", ecobeeProgramCoolHandler)
	subscribe(ecobee, "programHeatTemp", ecobeeProgramHeatHandler)
*/
	if (powerSwitch) {
		subscribe(powerSwitch, "switch.off", offHandler, [filterEvents: false])
		subscribe(powerSwitch, "switch.on", onHandler, [filterEvents: false])
	}
	log.debug("initialize state=$state")
    
}

private def sendNotifDelayNotInRange() {

	send "MonitorEcobeeTemp>scheduling delay (${givenInterval} min.) not in range, please restart..."
    
}



private addIndoorSensorsWhenOccupied() {

	def threshold = residentsQuietThreshold ?: 15   // By default, the delay is 15 minutes
	def result = false
	def t0 = new Date(now() - (threshold * 60 *1000))
	for (sensor in indoorSensors) {
 		def recentStates = sensor.statesSince("motion", t0)
		if (recentStates.find{it.value == "active"}) {
			state.tempSensors.add(sensor)
			log.debug "addTempSensorsWhenOccupied>added sensor $sensor"
			result= true            
		}	
            
	}
	log.debug "addTempSensorsWhenOccupied, result = $result"
	return result
}

private residentsHaveBeenQuiet() {

	def threshold = residentsQuietThreshold ?: 15   // By default, the delay is 15 minutes
	def result = true
	def t0 = new Date(now() - (threshold * 60 *1000))
	for (sensor in state.motions) {
		def recentStates = sensor.statesSince("motion", t0)
		if (recentStates.find{it.value == "active"}) {
			result = false
			break
		}	
            
	}
	log.debug "residentsHaveBeenQuiet: $result"
	return result
}


private isProgramScheduleSet(climateName, threshold) {
	def result = false
 	def t0 = new Date(now() - (threshold * 60 *1000))
	def recentStates = ecobee.statesSince("climateName", t0)
	if (recentStates.find{it.value == climateName}) {
		result = true
	}
	log.debug "isProgramScheduleSet: $result"
	return result
}

def motionEvtHandler(evt) {
 	if (evt.value == "active") {
		log.debug "Motion at home..."
		String currentProgName = ecobee.currentClimateName
        String currentProgType = ecobee.currentProgramType

		if (state?.programHoldSet == 'Away') {
			check_if_hold_justified()
		}
		else if ((currentProgName.toUpperCase()=='AWAY') && (state?.programHoldSet== "" ) && 
				(currentProgType.toUpperCase()!='VACATION')) {
			check_if_hold_needed()
		}
        
	}
}


def ecobeeHeatTempHandler(evt) {
	log.debug "ecobee's heating setpoint: $evt.value"
}

def ecobeeCoolTempHandler(evt) {
	log.debug "ecobee's cooling setpoint: $evt.value"
}

def ecobeeProgramCoolHandler(evt) {
	log.debug "ecobee's program Cool temp: $evt.value"
}
def ecobeeProgramHeatHandler(evt) {
	log.debug "ecobee's progran Heat temp: $evt.value"
}

def ecobeeHumidityHandler(evt) {
	log.debug "ecobee's humidity level: $evt.value"
}

def ecobeeTempHandler(evt) {
	log.debug "ecobee's temperature level: $evt.value"
}

def tempSensorHandler(evt) {
	log.debug "indoor Sensor's temperature level: $evt.value"
}


def ecobeeModeHandler(evt) {
	log.debug "ecobee's mode: $evt.value"
}

def indoorEvtHandler(evt) {
	log.debug "indoor $evt.name is: $evt.value"
}

def outdoorSensorHumHandler(evt) {
	log.debug "outdoor Sensor's humidity level: $evt.value"
}


def outdoorTempHandler(evt) {
	log.debug "outdoor temperature is: $evt.value"
}

def ecobeeProgramTypeHandler(evt) {
	log.debug "ecobee's program type: $evt.value"
}
def offHandler(evt) {
	log.debug "$evt.name: $evt.value"
}

def onHandler(evt) {
	log.debug "$evt.name: $evt.value"
	monitorAdjustTemp()
}

def monitorAdjustTemp() {
	if (powerSwitch?.currentSwitch == "off") {
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>Virtual master switch ${powerSwitch.name} is off, processing on hold...")
		}
		return
	}

	if (detailedNotif == 'true') {
		Integer delay = givenInterval ?: 59 // By default, do it every hour
		send("MonitorEcobeeTemp>monitoring every ${delay} minute(s)")
	}

	//  Polling of the latest values at the thermostat
	ecobee.poll()

	String currentProgType = ecobee.currentProgramType
	log.trace("MonitorEcobeeTemp> program Type= ${currentProgType}")
	if (currentProgType.contains("hold")) { 						
		log.trace("MonitorEcobeeTemp>about to call check_if_hold_justified....")
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>Hold set, checking if still justified")
		}
		check_if_hold_justified()
	}
        
	if (!currentProgType.contains("vacation")) {				// don't make adjustment if on vacation mode
		log.trace("MonitorEcobeeTemp>about to call check_if_needs_hold....")
		check_if_hold_needed()
	}
}

private def reset_state_program_values() {

 	state.programSetTime = null
 	state.programSetTimestamp = ""
 	state.programHoldSet = ""

}

private def reset_state_tempSensors() {

	state.tempSensors=[]
    settings.tempSensors.each {
// 	By default, the 'static' temp Sensors are the ones used for temp avg calculation
//  Other 'occupied' sensors may be added dynamically when needed 
            
		state.tempSensors.add(it) 
	}
}

private def reset_state_motions() {
	state.motions=[]
	if (settings.motions) {
		settings.motions.each {
			state.motions.add(it)        
		}
	}
    
	if (settings.indoorSensors) {
		settings.indoorSensors.each {
			state.motions.add(it)
		}
	}
}

private def check_if_hold_needed() {
	log.debug "Begin of Fcn check_if_hold_needed, settings= $settings"
	float max_temp_diff
	Integer humidity_threshold = givenHumThreshold ?: 85 // by default, 85% is the outdoor Humidity's threshold for more cooling
	float more_heat_threshold, more_cool_threshold
	float less_heat_threshold, less_cool_threshold

	def scale = getTemperatureScale()
	if (scale == 'C') {
		max_temp_diff = givenTempDiff ?: 2 // 2°C temp differential is applied by default
		more_heat_threshold = (givenMoreHeatThreshold != null) ? givenMoreHeatThreshold : (-17) // by default, -17°C is the outdoor temp's threshold for more heating
		more_cool_threshold = (givenMoreCoolThreshold != null) ? givenMoreCoolThreshold : 30 // by default, 30°C is the outdoor temp's threshold for more cooling
		less_heat_threshold = (givenLessHeatThreshold != null) ? givenLessHeatThreshold : 10 // by default, 10°C is the outdoor temp's threshold for less heating
		less_cool_threshold = (givenLessCoolThreshold != null) ? givenLessCoolThreshold : 22 // by default, 22°C is the outdoor temp's threshold for less cooling

	} else {
		max_temp_diff = givenTempDiff ?: 5 // 5°F temp differential is applied by default
		more_heat_threshold = (givenMoreHeatThreshold != null) ? givenMoreHeatThreshold : 10 // by default, 10°F is the outdoor temp's threshold for more heating
		more_cool_threshold = (givenMoreCoolThreshold != null) ? givenMoreCoolThreshold : 85 // by default, 85°F is the outdoor temp's threshold for more cooling
		less_heat_threshold = (givenLessHeatThreshold != null) ? givenLessHeatThreshold : 50 // by default, 50°F is the outdoor temp's threshold for less heating
		less_cool_threshold = (givenLessCoolThreshold != null) ? givenLessCoolThreshold : 75 // by default, 75°F is the outdoor temp's threshold for less cooling
	}

	String currentProgName = ecobee.currentClimateName

	Integer outdoorHumidity = outdoorSensor.currentHumidity
	float outdoorTemp = outdoorSensor.currentTemperature.toFloat()
	String ecobeeMode = ecobee.currentThermostatMode
	float heatTemp = ecobee.currentHeatingSetpoint.toFloat()
	float coolTemp = ecobee.currentCoolingSetpoint.toFloat()
	float programHeatTemp = ecobee.currentProgramHeatTemp.toFloat()
	float programCoolTemp = ecobee.currentProgramCoolTemp.toFloat()
	Integer ecobeeHumidity = ecobee.currentHumidity
	float ecobeeTemp = ecobee.currentTemperature.toFloat()

	reset_state_tempSensors()
	if (addIndoorSensorsWhenOccupied()) {
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>occupied indoor Sensors added for check_if_hold_needed")
		}
	}
	def indoorTemps = [ecobeeTemp]
	for (sensor in state.tempSensors) {  // Add dynamically any indoor Sensor's when occupied	 	
		def currentTemp = sensor?.currentTemperature
		if (currentTemp != null) {
			indoorTemps.add(currentTemp) // Add indoor temp to calculate the average based on all sensors
		}                    
    }
	float avg_indoor_temp = (indoorTemps.sum() / indoorTemps.size()).round(1) // this is the avg indoor temp based on indoor sensors

	log.trace "check_if_hold_needed> location.mode = $location.mode"
	log.trace "check_if_hold_needed> ecobee Mode = $ecobeeMode"
	log.trace "check_if_hold_needed> currentProgName = $currentProgName"
	log.trace "check_if_hold_needed> ecobee's indoorTemp = $ecobeeTemp°"
	log.trace "check_if_hold_needed> state.tempSensors = $state.tempSensors"
	log.trace "check_if_hold_needed> indoorTemps = $indoorTemps"
	log.trace "check_if_hold_needed> avgIndoorTemp = $avg_indoor_temp°"
	log.trace "check_if_hold_needed> outdoorTemp = $outdoorTemp°"
	log.trace "check_if_hold_needed> max_temp_diff = $max_temp_diff°"
	log.trace "check_if_hold_needed> moreHeatThreshold = $more_heat_threshold°"
	log.trace "check_if_hold_needed> moreCoolThreshold = $more_cool_threshold°"
	log.trace "check_if_hold_needed> lessHeatThreshold = $less_heat_threshold°"
	log.trace "check_if_hold_needed> lessCoolThreshold = $less_cool_threshold°"
	log.trace "check_if_hold_needed> heatTemp = $heatTemp°"
	log.trace "check_if_hold_needed> coolTemp = $coolTemp°"
	log.trace "check_if_hold_needed> programHeatTemp = $programHeatTemp°"
	log.trace "check_if_hold_needed> programCoolTemp = $programCoolTemp°"
	log.trace "check_if_hold_needed> state=${state}"
	float targetTstatTemp

	if (detailedNotif == 'true') {
		send("MonitorEcobeeTemp>needs Hold? currentProgName ${currentProgName},indoorTemp ${ecobeeTemp}°,progHeatSetPoint ${programHeatTemp}°,progCoolSetPoint ${programCoolTemp}°")
		send("MonitorEcobeeTemp>needs Hold? currentProgName ${currentProgName},indoorTemp ${ecobeeTemp}°,heatingSetPoint ${heatTemp}°,coolingSetPoint ${coolTemp}°")
		if (state.programHoldSet!= "") {
			send("MonitorEcobeeTemp>Hold ${state.programHoldSet} has been set")
		}
	}
	reset_state_motions()
    
	if (state.motions) {  // the following logic is done only if motion sensors are provided as input parameters
    
		if ((currentProgName.toUpperCase()=='AWAY') && (!residentsHaveBeenQuiet())) {
                
			ecobee.present()            
			send("MonitorEcobeeTemp>Program now set to Home, motion detected")
 			state.programSetTime = now()
 			state.programSetTimestamp = new Date().format("yyyy-MM-dd HH:mm", location.timeZone)
 			state.programHoldSet = 'Home'
			log.debug "MonitorEcobeeTemp>Program now set to Home at ${state.programSetTimestamp}, motion detected"
		 	/* Get latest heat and cool setting points after climate adjustment */
			programHeatTemp = ecobee.currentHeatingSetpoint.toFloat() // This is the heat temp associated to the current program
			programCoolTemp = ecobee.currentCoolingSetpoint.toFloat() // This is the cool temp associated to the current program
        
		} else if (((currentProgName.toUpperCase()!='SLEEP') && (currentProgName.toUpperCase()!='AWAY'))  && (residentsHaveBeenQuiet())) {
			// Do not adjust the program when ecobee mode = Sleep or Away    
                
			ecobee.away()          
			send("MonitorEcobeeTemp>Program now set to Away,no motion detected")
 			state.programSetTime = now()
 			state.programSetTimestamp = new Date().format("yyyy-MM-dd HH:mm", location.timeZone)
 			state.programHoldSet = 'Away'
			log.debug "MonitorEcobeeTemp>Program now set to Away at: ${state.programSetTimestamp}, motion detected"
        
		 	/* Get latest heat and cool setting points after climate adjustment */
			programHeatTemp = ecobee.currentHeatingSetpoint.toFloat() // This is the heat temp associated to the current program
			programCoolTemp = ecobee.currentCoolingSetpoint.toFloat() // This is the cool temp associated to the current program
		}
 		log.trace "MonitorEcobeeTemp>state=${state}"
        
	}// end if motions
    
	if (ecobeeMode == 'cool') {

		if (location.mode.toUpperCase() != 'AWAY') { // increase cooling settings only if ST mode != Away
			log.trace(
				"check_if_hold_needed>evaluate: moreCoolThreshold= ${more_cool_threshold}° vs. outdoorTemp ${outdoorTemp}°")
			log.trace(
				"check_if_hold_needed>evaluate: moreCoolThresholdHumidity= ${humidity_threshold}% vs. outdoorHum ${outdoorHumidity}%")
			log.trace(
				"check_if_hold_needed>evaluate: programCoolTemp= ${programCoolTemp}° vs. avg indoor Temp= ${avg_indoor_temp}°")
			if (detailedNotif == 'true') {
				send("MonitorEcobeeTemp>eval:  moreCoolThreshold ${more_cool_threshold}° vs. outdoorTemp ${outdoorTemp}°")
				send("MonitorEcobeeTemp>eval:  moreCoolThresholdHumidty ${humidity_threshold}% vs. outdoorHum ${outdoorHumidity}%")
				send("MonitorEcobeeTemp>eval:  programCoolTemp= ${programCoolTemp}° vs. avgIndoorTemp= ${avg_indoor_temp}°")
			}
			if (outdoorTemp >= more_cool_threshold) {
				targetTstatTemp = (programCoolTemp - max_temp_diff).round(1)
				ecobee.setCoolingSetpoint(targetTstatTemp)
				send("MonitorEcobeeTemp>cooling setPoint now =${targetTstatTemp}°,outdooTemp >=${more_cool_threshold}°")
			} else if (outdoorHumidity >= humidity_threshold) {
				def extremes = [less_cool_threshold, more_cool_threshold]
				float median_temp = (extremes.sum() / extremes.size()).round(1) // Increase cooling settings based on median temp
				if (detailedNotif == 'true') {
					String medianTempFormat = String.format('%2.1f', median_temp)
					send("MonitorEcobeeTemp>eval: cool median temp ${medianTempFormat}° vs.outdoorTemp ${outdoorTemp}°")
				}
				if (outdoorTemp > median_temp) { // Only increase cooling settings when outdoorTemp > median_temp
					targetTstatTemp = (programCoolTemp - max_temp_diff).round(1)
					ecobee.setCoolingSetpoint(targetTstatTemp)
					send("MonitorEcobeeTemp>cooling setPoint now=${targetTstatTemp}°, outdoorHum >=${humidity_threshold}%")

				}
			} else if ((state.tempSensors) && (avg_indoor_temp > coolTemp)) {
				float temp_diff = (ecobee_temp - avg_indoor_temp).round(1).abs() // adjust the coolingSetPoint at the ecobee tstat according to the avg indoor temp measured
				temp_diff = (temp_diff > max_temp_diff)? max_temp_diff: temp_diff
				targetTstatTemp = (programCoolTemp - temp_diff).round(1)
				if (temp_diff > 0.5) {  // adust the temp only if temp diff is significant
					ecobee.setCoolingSetpoint(targetTstatTemp)
					send("MonitorEcobeeTemp>cooling setPoint now =${targetTstatTemp}°,adjusted by temp diff (${temp_diff}°) between sensors")
				}    
			}                
		}
		log.trace("check_if_hold_needed>evaluate: lessCoolThreshold= ${less_cool_threshold} vs.outdoorTemp ${outdoorTemp}°")
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>evaluate: lessCoolThreshold ${less_cool_threshold}° vs. outdoorTemp ${outdoorTemp}°")
		}
		if (outdoorTemp <= less_cool_threshold) {
			targetTstatTemp = (programCoolTemp + max_temp_diff).round(1)
			ecobee.setCoolingSetpoint(targetTstatTemp)
			send(
				"Monitor&SetEcobeeTemp>cooling setPoint now=${targetTstatTemp}°, outdoor temp <=${less_cool_threshold}°"
			)

		}
	} else if (ecobeeMode == 'heat') {
		if (location.mode.toUpperCase() != 'AWAY') { // increase heating settings only if ST mode != Away
			log.trace("check_if_hold_needed>evaluate: moreHeatThreshold ${more_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
			log.trace(
				"check_if_hold_needed>evaluate: moreHeatThresholdHumidity= ${humidity_threshold}% vs.outdoorHumidity ${outdoorHumidity}%")
			log.trace(
				"check_if_hold_needed>evaluate: programHeatTemp= ${programHeatTemp}° vs. avg indoor Temp= ${avg_indoor_temp}°")
			if (detailedNotif == 'true') {
				send("MonitorEcobeeTemp>eval:  moreHeatThreshold ${more_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
				send("MonitorEcobeeTemp>eval:  moreHeatThresholdHumidty=${humidity_threshold}% vs.outdoorHumidity ${outdoorHumidity}%")
				send("MonitorEcobeeTemp>eval:  programHeatTemp= ${programHeatTemp}° vs. avgIndoorTemp= ${avg_indoor_temp}°")
			}
			if (outdoorTemp <= more_heat_threshold) {
				targetTstatTemp = (programHeatTemp + max_temp_diff).round(1)
				ecobee.setHeatingSetpoint(targetTstatTemp)
				send(
					"MonitorEcobeeTemp>heating setPoint now= ${targetTstatTemp}°, outdoorTemp <=${more_heat_threshold}°")
			} else if (outdoorHumidity >= humidity_threshold) {
				def extremes = [less_heat_threshold, more_heat_threshold]
				float median_temp = (extremes.sum() / extremes.size()).round(1) // Increase heating settings based on median temp
				if (detailedNotif == 'true') {
					String medianTempFormat = String.format('%2.1f', median_temp)
					send("MonitorEcobeeTemp>eval: heat median temp ${medianTempFormat}° vs.outdoorTemp ${outdoorTemp}°")
				}
				if (outdoorTemp < median_temp) { // Only increase heating settings when outdoorTemp < median_temp
					targetTstatTemp = (programHeatTemp + max_temp_diff).round(1)
					ecobee.setHeatingSetpoint(targetTstatTemp)
					send("MonitorEcobeeTemp>heating setPoint now=${targetTstatTemp}°, outdoorHum >=${humidity_threshold}%")
				}
			} else if ((state.tempSensors) && (avg_indoor_temp < heatTemp)) {
				float temp_diff = (avg_indoor_temp - ecobeeTemp).abs().round(1) // adjust the heatingSetPoint at the tstat according to the avg indoor temp measured
				temp_diff = (temp_diff > max_temp_diff)? max_temp_diff: temp_diff
				targetTstatTemp = (programHeatTemp + temp_diff).round(1)
				if (temp_diff > 0.5) {  // adust the temp only if temp diff is significant
					ecobee.setHeatingSetpoint(targetTstatTemp)
					send("MonitorEcobeeTemp>heating setPoint now =${targetTstatTemp}°,adjusted by temp diff (${temp_diff}°) between sensors")
				}                
			}                
		}
		log.trace("MonitorEcobeeTemp>eval:lessHeatThreshold=${less_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>eval:  lessHeatThreshold ${less_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
		}
			if (outdoorTemp >= less_heat_threshold) {
				targetTstatTemp = (programHeatTemp - max_temp_diff).round(1)
			ecobee.setHeatingSetpoint(targetTstatTemp)
			send("MonitorEcobeeTemp>heating setPoint now=${targetTstatTemp}°,outdoor temp>= ${less_heat_threshold}°")
		}
	}

	log.debug "End of Fcn check_if_hold_needed"
}

private def check_if_hold_justified() {
	log.debug "Begin of Fcn check_if_hold_justified, settings=$settings"
	Integer humidity_threshold = givenHumThreshold ?: 85 // by default, 85% is the outdoor Humidity's threshold for more cooling
	float more_heat_threshold, more_cool_threshold
	float less_heat_threshold, less_cool_threshold
	float max_temp_diff
	Integer delay = givenInterval ?: 59 // By default, do it every hour

	def scale = getTemperatureScale()
	if (scale == 'C') {
		max_temp_diff = givenTempDiff ?: 2 // 2°C temp differential is applied by default
		more_heat_threshold = (givenMoreHeatThreshold != null) ? givenMoreHeatThreshold : (-17) // by default, -17°C is the outdoor temp's threshold for more heating
		more_cool_threshold = (givenMoreCoolThreshold != null) ? givenMoreCoolThreshold : 30 // by default, 30°C is the outdoor temp's threshold for more cooling
		less_heat_threshold = (givenLessHeatThreshold != null) ? givenLessHeatThreshold : 10 // by default, 10°C is the outdoor temp's threshold for less heating
		less_cool_threshold = (givenLessCoolThreshold != null) ? givenLessCoolThreshold : 22 // by default, 22°C is the outdoor temp's threshold for less cooling

	} else {
		max_temp_diff = givenTempDiff ?: 5 // 5°F temp differential is applied by default
		more_heat_threshold = (givenMoreHeatThreshold != null) ? givenMoreHeatThreshold : 10 // by default, 10°F is the outdoor temp's threshold for more heating
		more_cool_threshold = (givenMoreCoolThreshold != null) ? givenMoreCoolThreshold : 85 // by default, 85°F is the outdoor temp's threshold for more cooling
		less_heat_threshold = (givenLessHeatThreshold != null) ? givenLessHeatThreshold : 50 // by default, 50°F is the outdoor temp's threshold for less heating
		less_cool_threshold = (givenLessCoolThreshold != null) ? givenLessCoolThreshold : 75 // by default, 75°F is the outdoor temp's threshold for less cooling
	}
	String currentProgName = ecobee.currentClimateName
	float heatTemp = ecobee.currentHeatingSetpoint.toFloat()
	float coolTemp = ecobee.currentCoolingSetpoint.toFloat()
	float programHeatTemp = ecobee.currentProgramHeatTemp.toFloat()
	float programCoolTemp = ecobee.currentProgramCoolTemp.toFloat()
	Integer ecobeeHumidity = ecobee.currentHumidity
	float ecobeeTemp = ecobee.currentTemperature.toFloat()

	reset_state_tempSensors()
	if (addIndoorSensorsWhenOccupied()) {
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>occupied indoor Sensors added for check_if_hold_justified")
		}
	}
    
	def indoorTemps = [ecobeeTemp]
	for (sensor in state.tempSensors) {  // Add dynamically any indoor Sensor's when occupied	 	
		def currentTemp = sensor?.currentTemperature
		if (currentTemp != null) {
			indoorTemps.add(currentTemp) // Add indoor temp to calculate the average based on all sensors
		}                    
    }
	float avg_indoor_temp = (indoorTemps.sum() / indoorTemps.size()).round(1) // this is the avg indoor temp based on indoor sensors

	Integer outdoorHumidity = outdoorSensor.currentHumidity
	float outdoorTemp = outdoorSensor.currentTemperature.toFloat()
	String ecobeeMode = ecobee.currentThermostatMode
	log.trace "check_if_hold_justified> location.mode = $location.mode"
	log.trace "check_if_hold_justified> ecobee Mode = $ecobeeMode"
	log.trace "check_if_hold_justified> currentProgName = $currentProgName"
	log.trace "check_if_hold_justified> outdoorTemp = $outdoorTemp°"
	log.trace "check_if_hold_justified> state.tempSensors = $state.tempSensors"
	log.trace "check_if_hold_justified> ecobee's indoorTemp = $ecobeeTemp°"
	log.trace "check_if_hold_justified> indoorTemps = $indoorTemps"
	log.trace "check_if_hold_justified> avgIndoorTemp = $avg_indoor_temp°"
	log.trace "check_if_hold_justified> max_temp_diff = $max_temp_diff°"
	log.trace "check_if_hold_justified> moreHeatThreshold = $more_heat_threshold°"
	log.trace "check_if_hold_justified> moreCoolThreshold = $more_cool_threshold°"
	log.trace "check_if_hold_justified> lessHeatThreshold = $less_heat_threshold°"
	log.trace "check_if_hold_justified> lessCoolThreshold = $less_cool_threshold°"
	log.trace "check_if_hold_justified> heatTemp = $heatTemp°"
	log.trace "check_if_hold_justified> coolTemp = $coolTemp°"
	log.trace "check_if_hold_justified> programHeatTemp = $programHeatTemp°"
	log.trace "check_if_hold_justified> programCoolTemp = $programCoolTemp°"
	log.trace "check_if_hold_justified>state=${state}"

	if (detailedNotif == 'true') {
		send("MonitorEcobeeTemp>Hold justified? currentProgName ${currentProgName},indoorTemp ${ecobeeTemp}°,progHeatSetPoint ${programHeatTemp}°,progCoolSetPoint ${programCoolTemp}°")
		send("MonitorEcobeeTemp>Hold justified? currentProgName ${currentProgName},indoorTemp ${ecobeeTemp}°,heatingSetPoint ${heatTemp}°,coolingSetPoint ${coolTemp}°")
		if (state?.programHoldSet!= null && state?.programHoldSet!= "") {
        
			send("MonitorEcobeeTemp>Hold ${state.programHoldSet} has been set")
		}
	}
	reset_state_motions()
	if (state.motions) {  // the following logic is done only if motion sensors are provided as input parameters
		if ((state?.programHoldSet == 'Away') && (!residentsHaveBeenQuiet())) {
			log.trace("check_if_hold_justified>it's not been quiet since ${state.programSetTimestamp},resume program...")
			ecobee.resumeProgram("")
			send("MonitorEcobeeTemp>resumed current program, motion detected")
			reset_state_program_values()
			check_if_hold_needed()   // check if another type of hold is now needed (ex. 'Home' hold or more heat because of outside temp ) 
			return // no more adjustments
		} else if (state?.programHoldSet == 'Away') {
			if (currentProgName.toUpperCase() == 'AWAY') {
        
				if (detailedNotif == 'true') {
					send("MonitorEcobeeTemp>hold no longer needed, program already in Away mode")
				}
				reset_state_program_values()
				ecobee.resumeProgram("")
        
			} else if (currentProgName.toUpperCase() == 'SLEEP') {
				if (detailedNotif == 'true') {
					send("MonitorEcobeeTemp>hold no longer needed, program in Sleep mode")
				}
				reset_state_program_values()
				ecobee.resumeProgram("")
        
			} else {
				log.trace("check_if_hold_justified>quiet since ${state.programSetTimestamp}, current program= ${currentProgName},'Away' hold justified")
				send("MonitorEcobeeTemp>quiet since ${state.programSetTimestamp}, current program= ${currentProgName}, 'Away' hold justified")
				return // hold justified, no more adjustments
			}    
		}
		if ((state?.programHoldSet == 'Home') && (residentsHaveBeenQuiet())) {
			log.trace("check_if_hold_justified>it's been quiet since ${state.programSetTimestamp},resume program...")
			ecobee.resumeProgram("")
			send("MonitorEcobeeTemp>resumed program, no motion detected")
			reset_state_program_values()
			check_if_hold_needed()   // check if another type of hold is now needed (ex. 'Away' hold or more heat b/c of low outdoor temp ) 
			return // no more adjustments
		} else if (state?.programHoldSet == 'Home')  { 
			if (currentProgName.toUpperCase() == 'HOME') {
				if (detailedNotif == 'true') {
					send("MonitorEcobeeTemp>hold no longer needed, program already in Home mode")
				}
				reset_state_program_values()
				ecobee.resumeProgram("")
 				check_if_hold_needed()   // check if another type of hold is now needed (ex. more heat b/c of low outdoor temp ) 
				return
			} else {
				log.trace("MonitorEcobeeTemp>not quiet since ${state.programSetTimestamp}, current program= ${currentProgName}, 'Home' hold justified")
				if (detailedNotif == 'true') {
					send("MonitorEcobeeTemp>not quiet since ${state.programSetTimestamp}, current program= ${currentProgName}, 'Home' hold justified")
				}
				return // hold justified, no more adjustments
			}
		}            
	}   // end if motions
    
	if (ecobeeMode == 'cool') {
		log.trace("check_if_hold_justified>evaluate: moreCoolThreshold=${more_cool_threshold} vs. outdoorTemp ${outdoorTemp}°")
		log.trace(
			"check_if_hold_justified>evaluate: moreCoolThresholdHumidity= ${humidity_threshold}% vs. outdoorHumidity ${outdoorHumidity}%")
		log.trace("check_if_hold_justified>evaluate: lessCoolThreshold= ${less_cool_threshold} vs.outdoorTemp ${outdoorTemp}°")
		log.trace(
			"check_if_hold_justified>evaluate: programCoolTemp= ${programCoolTemp}° vs.avgIndoorTemp= ${avg_indoor_temp}°")
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>eval:  moreCoolThreshold ${more_cool_threshold}° vs.outdoorTemp ${outdoorTemp}°")
			send("MonitorEcobeeTemp>eval:  lessCoolThreshold ${less_cool_threshold}° vs.outdoorTemp ${outdoorTemp}°")
			send("MonitorEcobeeTemp>eval:  moreCoolThresholdHumidity ${humidity_threshold}% vs. outdoorHumidity ${outdoorHumidity}%")
			send("MonitorEcobeeTemp>eval:  programCoolTemp= ${programCoolTemp}° vs. avgIndoorTemp= ${avg_indoor_temp}°")
		}
		if ((state.tempSensors) && (avg_indoor_temp > coolTemp)) {
			send("MonitorEcobeeTemp>Hold justified, avgIndoorTemp ($avg_indoor_temp°) > coolingSetpoint (${coolTemp}°)")
			return
		}                
		if ((outdoorTemp > less_cool_threshold) && (outdoorTemp < more_cool_threshold) &&
        		(outdoorHumidity < humidity_threshold)) {
			send("MonitorEcobeeTemp>resuming program, ${less_cool_threshold}° < outdoorTemp <${more_cool_threshold}°")
			ecobee.resumeProgram("")
		} else {
			if (detailedNotif == 'true') {
				send("MonitorEcobeeTemp>Hold justified, cooling setPoint=${coolTemp}°")
			}
			float actual_temp_diff = (programCoolTemp - coolTemp).round(1).abs()
			if (detailedNotif == 'true') {
				send("MonitorEcobeeTemp>eval: actual_temp_diff ${actual_temp_diff}° vs. Max temp diff ${max_temp_diff}°")
			}
			if ((actual_temp_diff > max_temp_diff) && (!state?.programHoldSet)) {
				if (detailedNotif == 'true') {
					send("MonitorEcobeeTemp>Hold differential too big (${actual_temp_diff}), needs adjustment")
				}
				check_if_hold_needed() // call it to adjust cool temp
			}
		}
	} else if (ecobeeMode == 'heat') {
		log.trace("MonitorEcobeeTemp>eval: moreHeatingThreshold ${more_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
		log.trace(
			"check_if_hold_justified>evaluate: moreHeatingThresholdHum= ${humidity_threshold}% vs. outdoorHum ${outdoorHumidity}%")
		log.trace("MonitorEcobeeTemp>eval:lessHeatThreshold=${less_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
		log.trace(
			"check_if_hold_justified>evaluate: programHeatTemp= ${programHeatTemp}° vs.avgIndoorTemp= ${avg_indoor_temp}°")
		if (detailedNotif == 'true') {
			send("MonitorEcobeeTemp>eval: moreHeatThreshold ${more_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
			send("MonitorEcobeeTemp>eval: lessHeatThreshold ${less_heat_threshold}° vs.outdoorTemp ${outdoorTemp}°")
			send("MonitorEcobeeTemp>eval: moreHeatThresholdHum ${humidity_threshold}% vs. outdoorHum ${outdoorHumidity}%")
			send("MonitorEcobeeTemp>eval: programHeatTemp= ${programHeatTemp}° vs. avgIndoorTemp= ${avg_indoor_temp}°")
		}
		if ((state.tempSensors) && (avg_indoor_temp < heatTemp)) {
			send("MonitorEcobeeTemp>Hold justified, avgIndoorTemp ($avg_indoor_temp°) < heatingSetpoint (${heatTemp}°)")
			return
		}                
		if ((outdoorTemp > more_heat_threshold) && (outdoorTemp < less_heat_threshold) && 
			(outdoorHumidity < humidity_threshold)) {
			send("MonitorEcobeeTemp>resuming program, ${less_heat_threshold}° < outdoorTemp > ${more_heat_threshold}°")
			ecobee.resumeProgram("")
		} else {
			if (detailedNotif == 'true') {
				send("MonitorEcobeeTemp>Hold justified, heating setPoint=${heatTemp}°")
			}
			float actual_temp_diff = (heatTemp - programHeatTemp).round(1).abs()
			if (detailedNotif == 'true') {
				send("MonitorEcobeeTemp>eval: actualTempDiff ${actual_temp_diff}° vs. Max temp Diff ${max_temp_diff}°")
			}
			if ((actual_temp_diff > max_temp_diff) && (!state?.programHoldSet)) {
				if (detailedNotif == 'true') {
					send("MonitorEcobeeTemp>Hold differential too big ${actual_temp_diff}, needs adjustment")
				}
				check_if_hold_needed() // call it to adjust heat temp
			}
		}
	}
	log.debug "End of Fcn check_if_hold_justified"
}


private send(msg) {
	if (sendPushMessage != "No") {
		log.debug("sending push message")
		sendPush(msg)

	}

	if (phoneNumber) {
		log.debug("sending text message")
		sendSms(phoneNumber, msg)
	}

	log.debug msg
}


// catchall
def event(evt) {
	log.debug "value: $evt.value, event: $evt, settings: $settings, handlerName: ${evt.handlerName}"
}

def cToF(temp) {
	return (temp * 1.8 + 32)
}

def fToC(temp) {
	return (temp - 32) / 1.8
}

