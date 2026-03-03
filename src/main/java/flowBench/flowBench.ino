#include <OneWire.h>
#include <DallasTemperature.h>

//Pin definitions
const int p1Pin = A0;
const int p2Pin = A1;
const int p3Pin = A2;
const int t1Pin = 2;
const int t2Pin = 3;
const int t3Pin = 4;

//DS18B20 Sensor Setup
OneWire oneWire1(t1Pin);
DallasTemperature sensorT1(&oneWire1);
OneWire oneWire2(t2Pin);
DallasTemperature sensorT2(&oneWire2);
OneWire oneWire3(t3Pin);
DallasTemperature sensorT3(&oneWire3);

void setup() {
  Serial.begin(115200);

  sensorT1.begin();
  sensorT2.begin();
  sensorT3.begin();

  //Tell the Arduino to operate asynchronously (doesn't block the code from carrying on, grabs the last completed temperature calculation)
  sensorT1.setWaitForConversion(false);
  sensorT2.setWaitForConversion(false);
  sensorT3.setWaitForConversion(false);

  //Configure ADC bit depth (get accurate precision)
  sensorT1.setResolution(11);
  sensorT2.setResolution(11);
  sensorT3.setResolution(11);
}

void loop() {
  sensorT1.requestTemperatures();
  sensorT2.requestTemperatures();
  sensorT3.requestTemperatures();
  
  //Read the raw analog voltage value (0 to 1023)
  int rawP1 = analogRead(p1Pin);
  int rawP2 = analogRead(p2Pin);
  int rawP3 = analogRead(p3Pin);

  //Convert to Pressure (hPa) using the MPX5010DP datasheet formula
  float pressureSense1 = mapMPX5010DP(rawP1);
  float pressureSense2 = mapMPX5010DP(rawP2);
  float pressureSense3 = mapMPX5010DP(rawP3);

  //Read temperatures (DS18B20 on A3, A4, A5)
  float tempSense1 = sensorT1.getTempCByIndex(0);
  float tempSense2 = sensorT2.getTempCByIndex(0);
  float tempSense3 = sensorT3.getTempCByIndex(0);

  //Print everything on one line, separated by commas
  Serial.print(tempSense1); Serial.print(",");
  Serial.print(tempSense2); Serial.print(",");
  Serial.print(tempSense3); Serial.print(",");
  Serial.print(pressureSense1); Serial.print(",");
  Serial.print(pressureSense2); Serial.print(",");
  Serial.println(pressureSense3);

  delay(100);
}

/**
* Helper math functions
*/
float mapMPX5010DP(int rawAnalogValue) {
  //Convert 0-1023 to actual Voltage (0.0V to 5.0V)
  float voltage = rawAnalogValue * (5.0 / 1023.0);
  
  //Reverse the datasheet formula to find kPa
  // Vout = 5.0 * (0.09 * P_kPa + 0.04) -> Vout = 0.45 * P_kPa + 0.2a
  // P_kPa = (Vout - 0.2) / 0.45
  float pressure_kPa = (voltage - 0.2) / 0.45;
  
  //Filter out tiny negative numbers caused by sensor noise at 0 pressure
  if (pressure_kPa < 0.0) {
    pressure_kPa = 0.0;
  }
  
  return pressure_kPa;
}