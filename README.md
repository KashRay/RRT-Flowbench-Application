# Raven's Racing Flowbench GUI

A real-time data logging and visualization interface for flow bench testing. This application connects to an Arduino via a USB to read pressure and temperature sensors, calculates flow rates, generates various graphs, and exports the trial data to CSV for analysis.

## Prerequisites
To run this application, the host computer must have **Java 23** (or a compatible JRE) installed. 
* [Download Java here](https://adoptium.net/) (Select JDK 23).

## How to Run the Application

The application is bundled as a "Fat JAR" containing all necessary libraries (such as `jSerialComm` for USB communication and `XChart` for graphing). You only need the single `.jar` file to run the software.

### On Windows
1. Download the `RRTFlowbenchApplication-1.0-SNAPSHOT-jar-with-dependencies.jar` file from the repository.
2. Ensure the Arduino is plugged into the computer via USB.
3. Simply **double-click** the `.jar` file to launch the GUI.
   * *Alternative:* Open Command Prompt, navigate to the folder containing the file, and run:
     ```cmd
     java -jar RRTFlowbenchApplication-1.0.jar
     ```

### On Linux & Raspberry Pi
1. Download the `RRTFlowbenchApplication-1.0.jar` file.
2. Ensure the Arduino is plugged into the device.
3. Open your terminal and navigate to the directory containing the downloaded file.
4. Execute the application using Java:
   ```bash
   java -jar RRTFlowbenchApplication-1.0.jar
