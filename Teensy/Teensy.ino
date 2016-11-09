/*
 * Using Teensy ADC library from https://github.com/pedvide/ADC. Copyright (c) 2016 Pedro Villanueva. See License on the folder ADC-master/README.md
 * Uses character escaping method from http://eli.thegreenplace.net/2009/08/12/framing-in-serial-communications/ for Java - Arduino Serial Protocol
 * Uses Arduino digital filter library from http://playground.arduino.cc/Code/Filters
 */

#include <stdint.h>
#include "ADC-master/ADC.h"
#include "Filters-master/Filters.h"

// define maximum and minimum current in nA. Depends on the current source capability
#define MINIMUM_CURRENT 10
#define MAXIMUM_CURRENT 10000000

// define maximum and minimum voltages on each shunt resistor
#define MIN_DAC_10M 120   // min dac voltage on 10M
#define MAX_DAC_10M 2472  // max dac voltage on 10M
#define MIN_CUR_10M 10
#define MAX_CUR_10M 200   // max current on 10M in nA

#define MIN_DAC_100K 24
#define MAX_DAC_100K 2472
#define MIN_CUR_100K 200
#define MAX_CUR_100K 20000

#define MIN_DAC_1K 24
#define MAX_DAC_1K 2472
#define MIN_CUR_1K 20000
#define MAX_CUR_1K 2000000

#define MIN_DAC_10 24
#define MAX_DAC_10 120
#define MIN_CUR_10 2000000
#define MAX_CUR_10 10000000

#define DELAY_SWITCH 400    // delay in ms after changing dac or shunt resistor

// define parasitic on resistance of reed relay
#define RON1 0.2
#define RON2 0.1

// definition for teensy pinout
#define SEL10_PIN 0
#define SEL1K_PIN 1
#define SEL100K_PIN 2
#define SEL10M_PIN 3
#define DIR2_PIN 4
#define DIR1_PIN 5
#define GAIN_PIN 6
#define SHUNT_PIN A2
#define PROBEP_PIN A10
#define PROBEM_PIN A11
#define DAC_PIN A14
#define lED_PIN 13

// code for serial command
#define SET_CURRENT 0
#define AUTO_CURRENT 1
#define CHANGE_CURRENT_DIR 2
#define DEBUG_MODE 3
#define MEASURE_VOLTAGE 4
#define CHECK_PROBE 5

// code for serial packet response
#define OK_CODE 0xFC
#define FAIL_CODE 0xFD
#define FRAMING_ERROR_CODE 0xFE
#define VOLTAGE_DATA_CODE 0xFA
#define CURRENT_DATA_CODE 0xFB

typedef struct serialData
{
   uint8_t commandId;
   uint32_t data;
} serialData_t;

typedef struct gainSetting
{
  uint8_t ampGain; // 0 = 1, 1 = 100
} gainSetting_t;

typedef struct currentSetting
{
  uint8_t shuntSelect; // 0 = 10M, 1 = 100K, 2 = 1K, 3 = 10
  uint16_t dacValue;  // dac value with shunt resistor together define current value
  uint8_t currentDir;  // 0 = forward and 1 = backward
} currentSetting_t;

serialData_t serialCommand;
gainSetting_t adcGain;
currentSetting_t currentSource;
uint8_t debugMode = 0;

ADC *adc = new ADC();

// ---------------------------------------------------------------------------
// Func: Initializes teensy peripherals, set initial value of structs,
//       initialize adc driver
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void setup(void) 
{
  Serial.begin(9600);
  
  // Initialize all pin here
  pinMode(SEL10M_PIN, OUTPUT);
  digitalWrite(SEL10M_PIN, HIGH);  // enable 10M shunt
  pinMode(SEL100K_PIN, OUTPUT);
  digitalWrite(SEL100K_PIN, LOW);
  pinMode(SEL1K_PIN, OUTPUT);
  digitalWrite(SEL1K_PIN, LOW);
  pinMode(SEL10_PIN, OUTPUT);
  digitalWrite(SEL10_PIN, LOW);  
  pinMode(DIR1_PIN, OUTPUT);
  digitalWrite(DIR1_PIN, HIGH);
  pinMode(DIR2_PIN, OUTPUT);
  digitalWrite(DIR2_PIN, LOW);
  pinMode(GAIN_PIN, OUTPUT);
  digitalWrite(GAIN_PIN, LOW);
  pinMode(lED_PIN, OUTPUT);
  digitalWrite(lED_PIN, LOW);
  analogWriteResolution(12);
  analogReference(EXTERNAL);
  analogWrite(DAC_PIN, 0);
  pinMode(PROBEP_PIN, INPUT);
  pinMode(PROBEM_PIN, INPUT);
  pinMode(SHUNT_PIN, INPUT); 

  // Set initial value of peripherals, variables
  currentSource.shuntSelect = 0;  // 10M enabled
  currentSource.dacValue = 0;
  currentSource.currentDir = 0;
  adcGain.ampGain = 0;

  adc->setReference(ADC_REF_1V2, ADC_0);
  adc->setReference(ADC_REF_EXT, ADC_1);
  adc->setAveraging(32, ADC_0);
  adc->setAveraging(32, ADC_1);
  adc->setResolution(16, ADC_0);
  adc->setResolution(12, ADC_1);
  adc->setConversionSpeed(ADC_HIGH_SPEED, ADC_0);
  adc->setConversionSpeed(ADC_HIGH_SPEED, ADC_1);
  adc->setSamplingSpeed(ADC_HIGH_SPEED, ADC_0);
  adc->setSamplingSpeed(ADC_HIGH_SPEED, ADC_1);
  adc->enablePGA(1, ADC_0);

  delay(1000);

  if(debugMode == 1)
  {
    Serial.println("Initialization finished");
  }
}

// ---------------------------------------------------------------------------
// Func: Main loop. Read serial command and call different functions depend
//       on the command. Also will blink led
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void loop(void) 
{
  uint8_t serialresult;

  BlinkLed();

  serialresult = ReadCheckSerial();

  if(serialresult == 0)
  {
    switch(serialCommand.commandId)
    {
      case SET_CURRENT:
        if(((serialCommand.data > 0) & (serialCommand.data < MINIMUM_CURRENT)) | (serialCommand.data > MAXIMUM_CURRENT))
        {
          if(debugMode == 1)
          {
            Serial.println("Invalid Current, Valid value is 0 or 10 - 10,000,000 nA");
          }
          else
          {
            SendError();
          }
        }
        else
        {
          SetCurrent();
        }
        break;
      case AUTO_CURRENT:
        AutoCurrent();
        break;
      case CHANGE_CURRENT_DIR:
        if(serialCommand.data > 1)
        {
          if(debugMode == 1)
          {
            Serial.println("Invalid Current direction entered. It should be 0 or 1");
          }
          else
          {
            SendError();
          }
        }
        else
        {
          currentSource.currentDir = serialCommand.data;
          if(currentSource.currentDir == 0)
          {
            digitalWrite(DIR2_PIN, LOW);
            delay(2);   // wait for relay to close
            digitalWrite(DIR1_PIN, HIGH);
          }
          else
          {
            digitalWrite(DIR1_PIN, LOW);
            delay(2);   // wait for relay to close
            digitalWrite(DIR2_PIN, HIGH);
          }
          delay(DELAY_SWITCH);
          if(debugMode == 1)
          {
            Serial.print("Current direction is ");
            Serial.println(currentSource.currentDir);
          }
          else
          {
            SendOk();
          }
        }
        break;
      case DEBUG_MODE:
        if(serialCommand.data > 1)
        {
          if(debugMode == 1)
          {
            Serial.println("Invalid Debug Mode data. It should be 0 or 1");
          }
          else
          {
            SendError();
          }
        }
        else
        {
          debugMode = serialCommand.data;
          if(debugMode == 1)
          {
            Serial.println("Set Debug Mode to ON"); 
          }
          else
          {
            SendOk();
          }
        }
        break;
      case MEASURE_VOLTAGE:
        MeasureVoltage();
        break;
      case CHECK_PROBE:
        if(CheckProbe() == 0)
        {
          if(debugMode == 1)
          {
            Serial.println("Probe connected");
          }
          else
          {
            SendOk();
          }
        }
        else
        {
          if(debugMode == 1)
          {
            Serial.println("Probe not connected");
          }
          else
          {
            
            SendFail();
          }
        }
        break;
      default:
        break;
    }
  }
  else if(serialresult == 1)
  {
    SendError();
  }
  delay(1);
}

// ---------------------------------------------------------------------------
// Func: Blink led automatically when called within main loop with 1ms delay
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void BlinkLed(void)
{
  static uint8_t ledstate = 0;
  static uint16_t leddelay = 0;

  switch(ledstate)
  {
    case 0:
      digitalWrite(lED_PIN, LOW);
      ledstate = 1;
      leddelay = 500;    
      break;
    case 1:
      if(leddelay == 0)
      {
        ledstate = 2;
      }
      else
      {
        leddelay--;
      }
      break;
    case 2:
      digitalWrite(lED_PIN, HIGH);
      ledstate = 3;
      leddelay = 500;
      break;
    case 3:
      if(leddelay == 0)
      {
        ledstate = 0;
      }
      else
      {
        leddelay--;
      }
      break;
    default:
      break;
  }
}

// ---------------------------------------------------------------------------
// Func: Decrease current value in the adjustable current source circuit by
//       decreasing dac value and switching shunt resistor
// Args: None
// Retn: 0 if successfull, 1 if cannot increase anymore
// ---------------------------------------------------------------------------
uint8_t Reducedacshunt(void)
{
  if(currentSource.shuntSelect == 0)
  {
    if(currentSource.dacValue <= (MIN_DAC_10M+11))
    {
      if(debugMode == 1)
      {
        Serial.println("Cannot decrease dac and shunt anymore");
      }
      return 1;  // failed to decrease dacshunt. already minimum
    }
    else
    {
      currentSource.dacValue -= 36;
    }
  }
  else if(currentSource.shuntSelect == 1)
  {
    if(currentSource.dacValue <= (MIN_DAC_100K+11))
    {
      currentSource.shuntSelect = 0;
      currentSource.dacValue = MAX_DAC_10M;
    }
    else
    {
      currentSource.dacValue -= 36;
    }
  }
  else if(currentSource.shuntSelect == 2)
  {
    if(currentSource.dacValue <= (MIN_DAC_1K+11))
    {
      currentSource.shuntSelect = 1;
      currentSource.dacValue = MAX_DAC_100K;
    }
    else
    {
      currentSource.dacValue -= 36;
    }
  }
  else
  {
    if(currentSource.dacValue <= (MIN_DAC_10+11))
    {
      currentSource.shuntSelect = 2;
      currentSource.dacValue = MAX_DAC_1K;
    }
    else
    {
      currentSource.dacValue -= 36;
    }
  }

  analogWrite(DAC_PIN, currentSource.dacValue);
  SwitchShunt(currentSource.shuntSelect);
  delay(DELAY_SWITCH);

  if(debugMode == 1)
  {
    Serial.print("Reduce dac to: ");
    Serial.print(currentSource.dacValue);
    Serial.print(" and shunt: ");
    Serial.println(currentSource.shuntSelect);
  }
  
  return 0;  // successfully reduced dacshunt
}

// ---------------------------------------------------------------------------
// Func: Increase current value in the adjustable current source circuit by
//       increasing dac value and switching shunt resistor
// Args: None
// Retn: 0 if successfull, 1 if cannot increase anymore
// ---------------------------------------------------------------------------
uint8_t Increasedacshunt(void)
{
  if(currentSource.shuntSelect == 3)
  {
    if(currentSource.dacValue >= (MAX_DAC_10-11))
    {
      if(debugMode == 1)
      {
        Serial.println("Cannot increase dac and shunt anymore");
      }
      return 1;  // failed to increase dacshunt. already maximum
    }
    else
    {
      currentSource.dacValue += 36;
    }
  }
  else if(currentSource.shuntSelect == 2)
  {
    if(currentSource.dacValue >= (MAX_DAC_1K-11))
    {
      currentSource.shuntSelect = 3;
      currentSource.dacValue = MIN_DAC_10;
    }
    else
    {
      currentSource.dacValue += 36;
    }
  }
  else if(currentSource.shuntSelect == 1)
  {
    if(currentSource.dacValue >= (MAX_DAC_100K-11))
    {
      currentSource.shuntSelect = 2;
      currentSource.dacValue = MIN_DAC_1K;
    }
    else
    {
      currentSource.dacValue += 36;
    }
  }
  else
  {
    if(currentSource.dacValue >= (MAX_DAC_10M-11))
    {
      currentSource.shuntSelect = 1;
      currentSource.dacValue = MIN_DAC_100K;
    }
    else
    {
      currentSource.dacValue += 36;
    }
  }
  analogWrite(DAC_PIN, currentSource.dacValue);
  SwitchShunt(currentSource.shuntSelect);
  delay(DELAY_SWITCH);

  if(debugMode == 1)
  {
    Serial.println("Increase dac and shunt");
    Serial.print("New dac value: ");
    Serial.println(currentSource.dacValue);
    Serial.print("New shunt: ");
    Serial.println(currentSource.shuntSelect);
  }
  
  return 0; // successfully increase dacshunt
}

// ---------------------------------------------------------------------------
// Func: Switch relay to select active shunt resistor for current source
//       Shunt resistor together with dac value will set the value of current
// Args: 0 = 10M, 1 = 100K, 2 = 1K, 3 = 10 ohm
// Retn: None
// ---------------------------------------------------------------------------
void SwitchShunt(uint8_t sel)
{
  switch(sel)
  {
    case 0:
      digitalWrite(SEL100K_PIN, LOW);
      digitalWrite(SEL1K_PIN, LOW);
      digitalWrite(SEL10_PIN, LOW);
      digitalWrite(SEL10M_PIN, HIGH);
      break;
    case 1:
      digitalWrite(SEL10M_PIN, LOW);
      digitalWrite(SEL1K_PIN, LOW);
      digitalWrite(SEL10_PIN, LOW);
      digitalWrite(SEL100K_PIN, HIGH);
      break;
    case 2:
      digitalWrite(SEL10M_PIN, LOW);
      digitalWrite(SEL100K_PIN, LOW);
      digitalWrite(SEL10_PIN, LOW);
      digitalWrite(SEL1K_PIN, HIGH);
      break;
    case 3:
      digitalWrite(SEL10M_PIN, LOW);
      digitalWrite(SEL100K_PIN, LOW);
      digitalWrite(SEL1K_PIN, LOW);
      digitalWrite(SEL10_PIN, HIGH);
      break;
    default:
      break;
  }
}

// ---------------------------------------------------------------------------
// Func: Read serial packet and check for framing and command code format
// Args: None
// Retn: 0 if valid command and correct framing, 1 if error, 2 if in process
// ---------------------------------------------------------------------------
uint8_t ReadCheckSerial(void)
{
  static uint8_t state = 0;
  static uint8_t data[5] = {0};
  static uint8_t ptr = 0;
  uint8_t serialdata;
  
  if(Serial.available() > 0)
  {
    serialdata = Serial.read();
   
    switch(state)
    {
      case 0: // wait_header
        if(serialdata == 0x7E)
        {
          state = 2;
        }
        else if(serialdata == 0x7D)
        {
          state = 1;
        }
        break;
      case 1: // ignore_next
        state = 0;
        break;
      case 2: // in_msg
        if(serialdata == 0x7E)
        {
          state = 0;
          ptr = 0;
          serialCommand.commandId = data[0];
          switch(data[0])
          {
            case SET_CURRENT:
              serialCommand.data = (uint32_t)data[1] << 24;
              serialCommand.data |= (uint32_t)data[2] << 16;
              serialCommand.data |= (uint32_t)data[3] << 8;
              serialCommand.data |= (uint32_t)data[4];
              break;
            case CHANGE_CURRENT_DIR:
            case DEBUG_MODE:
              serialCommand.data = data[1];
              break;
            case AUTO_CURRENT:
            case MEASURE_VOLTAGE:
            case CHECK_PROBE:
            default:
              break;
          }
          return 0;
        }
        else if(serialdata == 0x7D)
        {
          state = 3;
        }
        else
        {
          if(ptr <= 4)
          {
            data[ptr] = serialdata;
            ptr++;
          }
          else
          {
            state = 0;
            ptr = 0;
            return 1;  // shouldn't be more than 5 characters
          }
        }
        break;
      case 3: // after_esc
        if((serialdata == 0x7E) | (serialdata == 0x7D))
        {
          if(ptr <= 4)
          {
            state = 2;
            data[ptr] = serialdata;
            ptr++;
          }
          else
          {
            state = 0;
            ptr = 0;
            return 1;  // shouldn't be more than 5 characters
          }
        }
        else
        {
          state = 0;
          ptr = 0;
          return 1;
        }
        break;
      default:
        break;
    }
  }

  return 2;
}

// ---------------------------------------------------------------------------
// Func: Construct response serial packet to send to host application
//       indicating serial packet framing error
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void SendError(void)
{
  uint8_t data[3] = {0x7E, FRAMING_ERROR_CODE, 0x7E};

  Serial.write(data,3);
}

// ---------------------------------------------------------------------------
// Func: Construct response serial packet to send to host application
//       indicating successfull command or positive response
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void SendOk(void)
{
  uint8_t data[3] = {0x7E, OK_CODE, 0x7E};
  
  Serial.write(data,3);
}

// ---------------------------------------------------------------------------
// Func: Construct response serial packet to send to host application
//       indicating failed command or negative response
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void SendFail(void)
{
  uint8_t data[3] = {0x7E, FAIL_CODE, 0x7E};
  
  Serial.write(data,3);
}

// ---------------------------------------------------------------------------
// Func: Construct response serial packet to send to host application
//       indicating successfull command or positive response
// Args: None
// Retn: raw value of pga reference from adc driver
// ---------------------------------------------------------------------------
int32_t ReadRef(void)
{
  int32_t data;

  data = adc->analogRead(39, ADC_1);

  if(adc->adc1->fail_flag) 
  {
    Serial.print("ADC1 error flags: 0x");
    Serial.println(adc->adc1->fail_flag, HEX);
    if(adc->adc1->fail_flag == ADC_ERROR_COMPARISON) 
    {
      adc->adc1->fail_flag &= ~ADC_ERROR_COMPARISON; // clear that error
      Serial.println("Comparison error in ADC1");
    }
  }

  if(debugMode == 1)
  {
    Serial.print("Reference is: ");
    Serial.println((float)data / adc->getMaxValue(ADC_1) * 3.3);
  }
  
  return data;
}

// ---------------------------------------------------------------------------
// Func: Read voltage on shunt resistor to find out actual value of current
// Args: None
// Retn: raw value from adc driver in unsigned 32 bit format
// ---------------------------------------------------------------------------
int32_t ReadShunt(void)
{
  int32_t data;
  uint16_t i;

  FilterOnePole lowpassFilter( LOWPASS, 100 );
  for(i = 0; i < 1000; i++)
  {
    data = adc->analogRead(SHUNT_PIN, ADC_1);
    lowpassFilter.input(data);
  }
  data = lowpassFilter.output();

  if(adc->adc1->fail_flag) 
  {
    Serial.print("ADC1 error flags: 0x");
    Serial.println(adc->adc1->fail_flag, HEX);
    if(adc->adc1->fail_flag == ADC_ERROR_COMPARISON) 
    {
      adc->adc1->fail_flag &= ~ADC_ERROR_COMPARISON; // clear that error
      Serial.println("Comparison error in ADC1");
    }
  }
  
  return data;
}

// ---------------------------------------------------------------------------
// Func: Read voltage on the probe voltage pin based on gain in adcGain struct
// Args: None
// Retn: raw value from adc driver in unsigned 32 bit format
// ---------------------------------------------------------------------------
int32_t ReadVoltage(void)
{
  int32_t data;
  uint16_t i;

  FilterOnePole lowpassFilter( LOWPASS, 100 );
  for(i = 0; i < 1000; i++)
  {
    data = adc->analogReadDifferential(PROBEP_PIN, PROBEM_PIN, ADC_0);
    lowpassFilter.input(data);
  }
  data = lowpassFilter.output();
  
  if(adc->adc0->fail_flag) 
  {
    Serial.print("ADC0 error flags: 0x");
    Serial.println(adc->adc0->fail_flag, HEX);
    if(adc->adc0->fail_flag == ADC_ERROR_COMPARISON) 
    {
      adc->adc0->fail_flag &= ~ADC_ERROR_COMPARISON; // clear that error
      Serial.println("Comparison error in ADC0");
    }
  }

  return data;
}

// ---------------------------------------------------------------------------
// Func: Attempt to set current based on value requested by host application
//       after that will send SendOk() and then if sample connected will send
//       actual current value or SendFail() if not connected
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void SetCurrent(void)
{
  float dacdata;
  float current;
  int32_t adcresult;

  //Calculate required dac and shunt
  if(serialCommand.data == 0)
  {
    currentSource.shuntSelect = 0;
    currentSource.dacValue = 0;
  }
  else if(serialCommand.data <= MAX_CUR_10M)
  {
    currentSource.shuntSelect = 0;
    dacdata = (float)serialCommand.data / 1000000000 * 10000000;
    currentSource.dacValue = dacdata / 3.3 * 4095;
    
    if(currentSource.dacValue < MIN_DAC_10M)
    {
      currentSource.dacValue = MIN_DAC_10M;
    }
    else if(currentSource.dacValue > MAX_DAC_10M)
    {
      currentSource.dacValue = MAX_DAC_10M;
    }
    else
    {
      currentSource.dacValue = currentSource.dacValue - (currentSource.dacValue % 12);
    }
  }
  else if(serialCommand.data <= MAX_CUR_100K)
  {
    currentSource.shuntSelect = 1;
    dacdata = (float)serialCommand.data / 1000000000 * 100000;
    currentSource.dacValue = dacdata / 3.3 * 4095;

    if(currentSource.dacValue < MIN_DAC_100K)
    {
      currentSource.dacValue = MIN_DAC_100K;
    }
    else if(currentSource.dacValue > MAX_DAC_100K)
    {
      currentSource.dacValue = MAX_DAC_100K;
    }
    else
    {
      currentSource.dacValue = currentSource.dacValue - (currentSource.dacValue % 12);
    }
  }
  else if(serialCommand.data <= MAX_CUR_1K)
  {
    currentSource.shuntSelect = 2;
    dacdata = (float)serialCommand.data / 1000000000 * 1000;
    currentSource.dacValue = dacdata / 3.3 * 4095;

    if(currentSource.dacValue < MIN_DAC_1K)
    {
      currentSource.dacValue = MIN_DAC_1K;
    }
    else if(currentSource.dacValue > MAX_DAC_1K)
    {
      currentSource.dacValue = MAX_DAC_1K;
    }
    else
    {
      currentSource.dacValue = currentSource.dacValue - (currentSource.dacValue % 12);
    }
  }
  else
  {
    currentSource.shuntSelect = 3;
    dacdata = (float)serialCommand.data / 1000000000 * 10;
    currentSource.dacValue = dacdata / 3.3 * 4095;
    currentSource.dacValue = currentSource.dacValue - (currentSource.dacValue % 12);

    if(currentSource.dacValue < MIN_DAC_10)
    {
      currentSource.dacValue = MIN_DAC_10;
    }
    else if(currentSource.dacValue > MAX_DAC_10)
    {
      currentSource.dacValue = MAX_DAC_10;
    }
    else
    {
      currentSource.dacValue = currentSource.dacValue - (currentSource.dacValue % 12);
    }
  }
  analogWrite(DAC_PIN, currentSource.dacValue);
  SwitchShunt(currentSource.shuntSelect);
  delay(DELAY_SWITCH);

  if(debugMode == 1)
  {
    if(serialCommand.data == 0)
    {
      Serial.println("Set current to 0");
    }
    else
    {
      Serial.print("Calculated dac value is: ");
      Serial.println(currentSource.dacValue);
      Serial.print("Calculated shunt is: ");
      Serial.println(currentSource.shuntSelect);
    }
  }
  else
  {
    SendOk();
  }

  if(CheckProbe() == 0)
  {
    adcresult = ReadShunt();
    if(adcresult == ADC_ERROR_VALUE)
    {
      SendFail();
      if(debugMode == 1)
      {
        Serial.println("ADC Software error");
      }
      return;
    }
    
    current = CalculateCurrent(adcresult);
    SendActualCurrent(current);
  }
  else
  {
    SendFail();
  }
}

// ---------------------------------------------------------------------------
// Func: Calculate current based on voltage on the shunt resistor
// Args: raw value of voltage reading on shunt resistor returned from 
//       ReadShunt()
// Retn: actual current value in nA unit float format. pass this value to
//       SendActualCurrent() 
// ---------------------------------------------------------------------------
float CalculateCurrent(int32_t data)
{
  float current;
  
  if(currentSource.shuntSelect == 0)
  {
    current = (float)data * 3.3 / adc->getMaxValue(ADC_1) * ((float)1000000000/ (10000000 + RON1));
  }
  else if(currentSource.shuntSelect == 1)
  {
    current = (float)data * 3.3 / adc->getMaxValue(ADC_1) * ((float)1000000000/ (100000 + RON1));
  }
  else if(currentSource.shuntSelect == 2)
  {
    current = (float)data * 3.3 / adc->getMaxValue(ADC_1) * ((float)1000000000/ (1000 + RON1));
  }
  else
  {
    current = (float)data * 3.3 / adc->getMaxValue(ADC_1) * ((float)1000000000/ (10 + RON1));
  }

  return current;
}

// ---------------------------------------------------------------------------
// Func: Construct serial packet containing voltage value to send to host
//       application
// Args: voltage value to send in uV unit float format
// Retn: None
// ---------------------------------------------------------------------------
void SendActualVoltage(float data)
{
  uint8_t frame[7] = {0x7E, VOLTAGE_DATA_CODE, 0, 0, 0, 0, 0x7E};
  uint8_t i;
  uint8_t * ptr;

  if(debugMode == 1)
  {
    Serial.print("Voltage measured is: ");
    if(data < 1000)
    {
      Serial.print(data, 2);
      Serial.println(" uV");
    }
    else if(data < 1000000)
    {
      Serial.print(data / 1000, 2);
      Serial.println(" mV");
    }
    else
    {
      Serial.print(data / 1000000, 2);
      Serial.println(" V");
    }
  }
  else
  {
    ptr = (uint8_t *)&data;
    frame[2] = *ptr;
    ptr++;
    frame[3] = *ptr;
    ptr++;
    frame[4] = *ptr;
    ptr++;
    frame[5] = *ptr;
    ptr++;
  
    Serial.write(frame[0]);
    for(i=1; i < 6; i++)
    {
      if((frame[i] == 0x7D) | (frame[i] == 0x7E))
      {
        Serial.write(0x7D);
        Serial.write(frame[i]);
      }
      else
      {
        Serial.write(frame[i]);
      }
    }
    Serial.write(frame[6]);
  }
}

// ---------------------------------------------------------------------------
// Func: Construct serial packet containing current value to send to host
//       application
// Args: value of current returned by CalculateCurrent() in nA unit
// Retn: None
// ---------------------------------------------------------------------------
void SendActualCurrent(float data)
{
  uint8_t frame[7] = {0x7E, CURRENT_DATA_CODE, 0, 0, 0, 0, 0x7E};
  uint8_t i;
  uint8_t * ptr;

  if(debugMode == 1)
  {
    Serial.print("Actual current measured is: ");
    if(data < 1000)
    {
      Serial.print(data, 2);
      Serial.println(" nA");
    }
    else if(data < 1000000)
    {
      Serial.print(data / 1000, 2);
      Serial.println(" uA");
    }
    else
    {
      Serial.print(data / 1000000, 2);
      Serial.println(" mA");
    }
  }
  else
  {
    ptr = (uint8_t *)&data;
    frame[2] = *ptr;
    ptr++;
    frame[3] = *ptr;
    ptr++;
    frame[4] = *ptr;
    ptr++;
    frame[5] = *ptr;
    ptr++;
    
    Serial.write(frame[0]);
    for(i=1; i < 6; i++)
    {
      if((frame[i] == 0x7D) | (frame[i] == 0x7E))
      {
        Serial.write(0x7D);
        Serial.write(frame[i]);
      }
      else
      {
        Serial.write(frame[i]);
      }
    }
    Serial.write(frame[6]);
  }
}

// ---------------------------------------------------------------------------
// Func: Automatically adjust current to fit current sample connected
//       to the probe. Will set gain to the lowest level and call
//       SendActualCurrent() to report the current adjusted
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void AutoCurrent(void)
{
  float current = 0;
  int32_t adcresult = 0;
  uint16_t presetdacvalue[7] = {120, 1236, 120, 1236, 120, 1236, 120};
  uint8_t presetshuntvalue[7] = {0, 0, 1, 1, 2, 2, 3};
  uint8_t index = 0;
  
  currentSource.shuntSelect = presetshuntvalue[index];
  currentSource.dacValue = presetdacvalue[index];
  analogWrite(DAC_PIN, currentSource.dacValue);
  SwitchShunt(currentSource.shuntSelect);
  
  adcGain.ampGain = 0;
  digitalWrite(GAIN_PIN, LOW);
  delay(DELAY_SWITCH);

  if(CheckProbe() != 0)
  {   
    if(debugMode == 1)
    {
      Serial.println("Probe not touching sample or current source voltage compliance ratings exceeded");
    }
    else
    {
      SendFail();
    }
    return;
  }
  
  adcresult = ReadVoltage();
  if(adcresult == ADC_ERROR_VALUE)
  {
    SendFail();
    if(debugMode == 1)
    {
      Serial.println("ADC Software error");
    }
    return;
  }
  
  if(debugMode == 1)
  {
    Serial.println("Coarse adjustment");
  }

  // coarse adjustment. cycle through presetshuntvalue and presetdacvalue to find the right fit
  // if still smaller than 66.67 % max value (0.8V) continue loop 
  while(abs(adcresult) < (adc->getMaxValue(ADC_0)*0.667))  
  {
    if(index < 6)
    {
      index++;
    }
    else
    {
      break;  
    }
    
    currentSource.shuntSelect = presetshuntvalue[index];
    currentSource.dacValue = presetdacvalue[index];
    analogWrite(DAC_PIN, currentSource.dacValue);
    SwitchShunt(currentSource.shuntSelect);
    delay(DELAY_SWITCH);   

    if(debugMode == 1)
    {
      Serial.print("Current shunt: ");
      Serial.println(currentSource.shuntSelect);
      Serial.print("Current dac: ");
      Serial.println(currentSource.dacValue);
    }

    if(CheckProbe() != 0) // means current source voltage compliance exceeded
    {
      break;
    }

    adcresult = ReadVoltage();
    if(adcresult == ADC_ERROR_VALUE)
    {
      SendFail();
      if(debugMode == 1)
      {
        Serial.println("ADC Software error");
      }
      return;
    }
  }

  if((abs(adcresult) > (adc->getMaxValue(ADC_0)*0.667)) | (CheckProbe() != 0))
  {
    if(index > 0)
    {
      index--;
    }
  }
  currentSource.shuntSelect = presetshuntvalue[index];
  currentSource.dacValue = presetdacvalue[index];
  analogWrite(DAC_PIN, currentSource.dacValue);
  SwitchShunt(currentSource.shuntSelect);
  delay(DELAY_SWITCH);   

  if(debugMode == 1)
  {
    Serial.print("Current shunt: ");
    Serial.println(currentSource.shuntSelect);
    Serial.print("Current dac: ");
    Serial.println(currentSource.dacValue);
  }

  adcresult = ReadShunt();
  if(adcresult == ADC_ERROR_VALUE)
  {
    SendFail();
    if(debugMode == 1)
    {
      Serial.println("ADC Software error");
    }
    return;
  }

  if(debugMode != 1)
  {
    SendOk();
  }
  
  current = CalculateCurrent(adcresult);
  SendActualCurrent(current);
}

// ---------------------------------------------------------------------------
// Func: Measure voltage on probe voltage pin, automatically adjusting gain
//       if voltage too low. After that call SendActualVoltage() and
//       SendActualCurrent() to report the measurement
// Args: None
// Retn: None
// ---------------------------------------------------------------------------
void MeasureVoltage(void)
{
  float voltage;
  float current;
  float sheetres;
  int32_t adcresult;
  int32_t refvalue;
  
  adcGain.ampGain = 0;
  digitalWrite(GAIN_PIN, LOW);
  delay(DELAY_SWITCH);

  if(CheckProbe() != 0)
  {
    SendFail();
    if(debugMode == 1)
    {
      Serial.println("Failed to measure. Probe not connected");
    }
    return;
  }

  adcresult = ReadVoltage();
  if(adcresult == ADC_ERROR_VALUE)
  {
    SendFail();
    if(debugMode == 1)
    {
      Serial.println("ADC Software error");
    }
    return;
  }
  /* 
   *  Ideally input to instrumentation amplifier should be centered around Vdd/2.
   *  But since it is not, when gain x200 is enabled, the output of instrumentation amplifier
   *  might saturate, so to prevent that from happening the voltage on shunt resistor must
   *  be large enough
  */
  if(abs(adcresult) < (adc->getMaxValue(ADC_0)*0.0033))  // if smaller than 0.33% of max value = 4mV
  {
    adcGain.ampGain = 1;
    digitalWrite(GAIN_PIN, HIGH);  // enable x200 gain on instrumentation amplifier
    if(debugMode == 1)
    {
      Serial.println("Enable x200 Gain");
    }
    if(currentSource.dacValue < 620)   // if voltage on shunt resistor too small
    {
      Serial.println("Voltage on shunt resistor too small");
      Serial.println("Reducing dacshunt");
      if(currentSource.shuntSelect > 0)
      {
        currentSource.dacValue = 2472;
        currentSource.shuntSelect--;
        analogWrite(DAC_PIN, currentSource.dacValue);
        SwitchShunt(currentSource.shuntSelect);
      }
    }
    delay(DELAY_SWITCH);
  }

  adcresult = ReadVoltage();
  if(adcresult == ADC_ERROR_VALUE)
  {
    SendFail();
    if(debugMode == 1)
    {
      Serial.println("ADC Software error");
    }
    return;
  }

  if(debugMode == 1)
  {
    Serial.print("Raw voltage value is : ");
    Serial.println(adcresult);
  }
  
  refvalue = ReadRef();

  voltage = (float)adcresult / adc->getMaxValue(ADC_0) * ((float)refvalue / adc->getMaxValue(ADC_1) * 3.3);
  voltage = abs(voltage);

  if(adcGain.ampGain == 1)
  {
    voltage = voltage / (200 + RON2);
  }
  voltage = voltage * 1000000; // convert to uV
  
  adcresult = ReadShunt();
  if(adcresult == ADC_ERROR_VALUE)
  {
    SendFail();
    if(debugMode == 1)
    {
      Serial.println("ADC Software error");
    }
    return;
  }

  if(debugMode == 1)
  {
    Serial.print("Raw shunt value is : ");
    Serial.println(adcresult);
  }

  current = CalculateCurrent(adcresult);

  if(debugMode != 1)
  {
    SendOk();
  }

  SendActualVoltage(voltage);
  SendActualCurrent(current);

  if(debugMode == 1)
  {
    sheetres = voltage / current * 1000;
    Serial.print("Resistance is : ");
    Serial.println(sheetres);
    sheetres = sheetres * 4.53;
    Serial.print("Sheet resistance is : ");
    Serial.println(sheetres);
  }

  digitalWrite(GAIN_PIN, LOW);
}

// ---------------------------------------------------------------------------
// Func: Check whether voltage at shunt resistor is approximately equal to
//       dac value. If not, the two possible cause is probe not connected
//       to sample or current source voltage compliance exceeded
// Args: None
// Retn: 0 if equal or 1 if not equal
// ---------------------------------------------------------------------------
uint8_t CheckProbe(void)
{
  int32_t adcresult;

  adcresult = ReadShunt();
  if(adcresult == ADC_ERROR_VALUE)
  {
    if(debugMode == 1)
    {
      Serial.println("ADC Software error");
    }
    return 1;
  }
  
  if((abs(adcresult - currentSource.dacValue) > 72) | (adcresult < 24))
  {
    return 1;
  }

  return 0;  
}
