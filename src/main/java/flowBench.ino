float pressureSense1;
float pressureSense2;
float pressureSense3;
float tempSense1;
float tempSense2;
float tempSense3;
bool rampingUp = true;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);
  pressureSense1 = 0.0;
  pressureSense2 = 1013.25;
  pressureSense3 = 0.0;
  tempSense1 = 24.0;
  tempSense2 = 25.0;
  tempSense3 = 40.0;
}

void loop() {
    // put your main code here, to run repeatedly:
    //int pressureSense1 = digitalRead(1);
    //int pressureSense2 = digitalRead(2);
    //int pressureSense3 = digitalRead(3);
    //int tempSense1 = digitalRead(4);
    //int tempSense2 = digitalRead(5);
    //int tempSense3 = digitalRead(6);

    //Simulate pressure sweep (P1)
    if (rampingUp) {
        pressureSense1 += 0.5 + (random(0, 100) / 100.0);
        if (pressureSense1 > 72.0) rampingUp = false;
    } else {
        pressureSense1 -= 0.5 + (random(0, 100) / 100.0);
        if (pressureSense1 < 0.0) {
            pressureSense1 = 0.0;
            rampingUp = true;
        }
    }

    //Simulate atmospheric fluctuation (P2)
    pressureSense2 = 1013.0 + ((random(0, 100) / 100.0) - 0.5);

    //Simulate temperature rise (vacuum motors heating up)
    if (tempSense1 < 60) tempSense1 += 0.05 * (random(0, 100) / 100.0);
    tempSense2 += ((random(0, 100) / 100.0) - 0.5) * 0.1;

    //Print everything on one line, separated by commas
    Serial.print(tempSense1); Serial.print(",");
    Serial.print(tempSense2); Serial.print(",");
    Serial.print(tempSense3); Serial.print(",");
    Serial.print(pressureSense1); Serial.print(",");
    Serial.print(pressureSense2); Serial.print(",");
    Serial.println(pressureSense3);

    delay(100);
}
