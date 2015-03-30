#include <Adafruit_TiCoServo.h>
#include <known_16bit_timers.h>
#include <Arduino.h>
#include <Wire.h>
#include "Adafruit_GFX.h"
#include "SPI.h" // Comment out this line if using Trinket or Gemma
#include <Adafruit_MotorShield.h>
#include <Adafruit_BLE_UART.h>
#include <Adafruit_NeoMatrix.h>
#include <Adafruit_NeoPixel.h>

#define ADAFRUITBLE_REQ 8
#define ADAFRUITBLE_RDY 2     // This should be an interrupt pin, on Uno thats #2 or #3
#define ADAFRUITBLE_RST 3

#define EYE_PIN 5
#define MOUTH_PIN 6
Adafruit_MotorShield motorShield = Adafruit_MotorShield();
Adafruit_DCMotor *leftMotor = motorShield.getMotor(1);
Adafruit_DCMotor *rightMotor = motorShield.getMotor(2);

Adafruit_BLE_UART BTLEserial = Adafruit_BLE_UART(ADAFRUITBLE_REQ, ADAFRUITBLE_RDY, ADAFRUITBLE_RST);

int eye_pupil_color = 12314;
int eye_ball_color = 23142;

Adafruit_NeoMatrix eyematrix = Adafruit_NeoMatrix(8, 8, EYE_PIN,
                               NEO_MATRIX_TOP     + NEO_MATRIX_RIGHT +
                               NEO_MATRIX_ROWS + NEO_MATRIX_PROGRESSIVE,
                               NEO_GRB            + NEO_KHZ800);


Adafruit_NeoMatrix mouthmatrix = Adafruit_NeoMatrix(8, 5, MOUTH_PIN,
                                 NEO_MATRIX_TOP     + NEO_MATRIX_RIGHT +
                                 NEO_MATRIX_ROWS + NEO_MATRIX_PROGRESSIVE,
                                 NEO_GRB            + NEO_KHZ800);
// char-array to hold the incoming message
char robotMessage[40];
byte robotMessageIndex = 0;

// Two robot servos
Adafruit_TiCoServo bottomServo;
Adafruit_TiCoServo topServo;

// Starting positions for servos.
byte bottomPos = 90;
byte topPos = 25;


byte bottomPosMax = 175;
byte bottomPosMin = 15;

byte topPosMax = 100;
byte topPosMin = 25;

// How much to move servo position on each motor command
byte topServoIncrement = 5;
byte bottomServoIncrement = 5;

// Timers used to "simulate" threading
unsigned long eyeTimer;
unsigned long eyeLastTimer;
unsigned long eyeAcc;

unsigned long messageStartTime;
unsigned long messageCurrentTime;
unsigned long messageEstimatedTime = 400;

unsigned long timeSinceLastCmd = 0;
unsigned long currentCmdTime;


static const uint8_t PROGMEM // Bitmaps are stored in program memory
blinkImg[][8] = {    // Eye animation frames
  { B00111100,         // Fully open eye
    B01111110,
    B11111111,
    B11111111,
    B11111111,
    B11111111,
    B01111110,
    B00111100
  },
  { B00000000,
    B01111110,
    B11111111,
    B11111111,
    B11111111,
    B11111111,
    B01111110,
    B00111100
  },
  { B00000000,
    B00000000,
    B00111100,
    B11111111,
    B11111111,
    B11111111,
    B00111100,
    B00000000
  },
  { B00000000,
    B00000000,
    B00000000,
    B00111100,
    B11111111,
    B01111110,
    B00011000,
    B00000000
  },
  { B00000000,         // Fully closed eye
    B00000000,
    B00000000,
    B00000000,
    B10000001,
    B01111110,
    B00000000,
    B00000000
  }
};


uint16_t j = (384 * 5) - 1 ;

uint8_t  blinkIndex[] = { 1, 2, 3, 4, 3, 2, 1 }; // Blink bitmap sequence
uint8_t  blinkCountdown = 100; // Countdown to next blink (in frames)
uint8_t  gazeCountdown  =  75; // Countdown to next eye movement
uint8_t  gazeFrames     =  50; // Duration of eye movement (smaller = faster)

int8_t  eyeX = 3;
int8_t eyeY = 3;   // Current eye position
int8_t  newX = 3;
int8_t newY = 3;   // Next eye position
int8_t  dX   = 0;
int8_t dY   = 0;   // Distance from prior to new position



byte mouthIndex = 0;
boolean isMouthAnimationPlayingBackwards = false;
// different mouth positions
static const uint8_t mouthPos[][5] PROGMEM =
{
  {
    0xff,
    0xff,
    0xff,
    0xff,
    0xff
  },
  {
    0xff,
    0xff,
    0xaa,
    0x55,
    0xff
  },
  {
    // open maw for mouth pos 1
    0xff,
    0xaa,
    0x0,
    0x55,
    0xff
  },
  {
    0xaa,
    0x0,
    0x0,
    0x0,
    0x55
  }
};

unsigned long mouthTimer;
unsigned long mouthLastTimer;
unsigned long mouthAcc;



byte incomingByte;

// Bytes to know what messages are about to be received
static const byte MOTOR_COMMAND_DELIMITER = 123;
static const byte MESSAGE_DELIMITER = 122;
static const byte PIN_TOGGLE_DELIMITER = 121;
static const byte PIN_PWM_DELIMITER = 120;

// Bytes indicating direction of motor
static const byte MOTOR_FORWARD = 70;
static const byte MOTOR_RELEASE = 82;
static const byte MOTOR_BACKWARD = 66;

// bytes indicating servo movement.
static const byte SERVO_UP = 1;
static const byte SERVO_UP_RIGHT = 2;
static const byte SERVO_RIGHT = 3;
static const byte SERVO_DOWN_RIGHT = 4;
static const byte SERVO_DOWN = 5;
static const byte SERVO_DOWN_LEFT = 6;
static const byte SERVO_LEFT = 7;
static byte SERVO_UP_LEFT = 8;
static const byte SERVO_NOTHING = 9;

boolean isTransmittingMessage = false;

byte motorCommand[5];
byte motorCmdIndex = 0;
long previousMillis = 0;

typedef enum {NOTHING, MOTOR, MESSAGE, TOGGLE, PINPWM} state;
state currentState;

void setup() {
  motorShield.begin();
  Serial.begin(9600);
  eyematrix.begin();
  eyematrix.setBrightness(1);

  mouthmatrix.begin();
  mouthmatrix.setBrightness(1);
  resetRobotMouth();
  BTLEserial.setDeviceName("ROBOT"); /* 7 characters max! */

  BTLEserial.begin();
  Serial.begin(9600);
  currentState = NOTHING;
  // Seed random number generator from an unused analog input:
  randomSeed(analogRead(A0));
  // Initialize each matrix object:

  bottomServo.attach(12, 500, 2200);
  topServo.attach(13, 500, 2200);  // attaches the servo on pin 9 to the servo object
  bottomServo.write(bottomPos);
  topServo.write(topPos);

  while (Serial.read() != ':');   // When the Emic 2 has initialized and is ready, it will send a single ':' character, so wait here until we receive it
  delay(10);                          // Short delay
  Serial.flush();                 // Flush the receive buffer
  Serial.println("V18");
  
}

void loop() {

  BTLEserial.pollACI();
  //BTLEserial.pollACI();
  handleSerialInput();
  //BTLEserial.pollACI();

  currentCmdTime = mouthTimer = eyeTimer = messageCurrentTime = millis();

  eyeAcc += eyeTimer - eyeLastTimer;
  eyeLastTimer = eyeTimer;

  mouthAcc += mouthTimer - mouthLastTimer;
  mouthLastTimer = mouthTimer;

  while (eyeAcc >= 70)
  {
    animateEyes();
    eyeAcc = 0;
  }


  while (mouthAcc >= 100)
  {
    if (isTransmittingMessage)
    {
      animateMouth();

      if (messageCurrentTime - messageStartTime >= messageEstimatedTime)
      {
        resetRobotMouth();
        isTransmittingMessage = false;
        //mouthSerial.println(mouth);
      }
    }
    mouthAcc = 0;
  }

  /*
  if (isTransmittingMessage)
  {


    if (messageCurrentTime - messageStartTime >= messageEstimatedTime)
    {
         noTone(5);
         //mouthSerial.println(mouth);
         isTransmittingMessage = false;
    }
  }*/
}

void animateEyes() {
  // Draw eyeball in current state of blinkyness (no pupil).  Note that
  // only one eye needs to be drawn.  Because the two eye matrices share
  // the same address, the same data will be received by both.
  eyematrix.clear();
  // When counting down to the next blink, show the eye in the fully-
  // open state.  On the last few counts (during the blink), look up
  // the corresponding bitmap index.
  eyematrix.drawBitmap(0, 0,
                       blinkImg[
                         (blinkCountdown < sizeof(blinkIndex)) ? // Currently blinking?
                         blinkIndex[blinkCountdown] :            // Yes, look up bitmap #
                         0                                       // No, show bitmap 0
                       ], 8, 8, eye_ball_color);
  // Decrement blink counter.  At end, set random time for next blink.
  if (--blinkCountdown == 0) blinkCountdown = random(5, 180);

  // Add a pupil (2x2 black square) atop the blinky eyeball bitmap.
  // Periodically, the pupil moves to a new position...

  if (--gazeCountdown <= gazeFrames)
  {
    // Eyes are in motion - draw pupil at interim position
    eyematrix.fillRect(
      newX - (dX * gazeCountdown / gazeFrames),
      newY - (dY * gazeCountdown / gazeFrames),
      2, 2, eye_pupil_color);
    if (gazeCountdown == 0)
    { // Last frame?
      eyeX = newX; eyeY = newY; // Yes.  What's new is old, then...
      do
      { // Pick random positions until one is within the eye circle
        newX = random(7); newY = random(7);
        dX   = newX - 3;  dY   = newY - 3;
      } while ((dX * dX + dY * dY) >= 10);     // Thank you Pythagoras

      dX            = newX - eyeX;             // Horizontal distance to move
      dY            = newY - eyeY;             // Vertical distance to move
      gazeFrames    = random(3, 15);           // Duration of eye movement
      gazeCountdown = random(gazeFrames, 120); // Count to end of next movement
    }
  } else {
    // Not in motion yet -- draw pupil at current static position
    eyematrix.fillRect(eyeX, eyeY, 2, 2, eye_pupil_color);
  }

  eyematrix.show();
}


void animateMouth() {
  mouthmatrix.clear();
  mouthmatrix.fillScreen(0xF800);
  mouthmatrix.drawBitmap(0, 0, mouthPos[mouthIndex], 8, 5, 0xFFFF );
  mouthmatrix.show();

  if (mouthIndex == 3)
  {
    isMouthAnimationPlayingBackwards = true;
  }

  else if (mouthIndex == 0)
  {
    isMouthAnimationPlayingBackwards = false;
  }


  if (isMouthAnimationPlayingBackwards == false)
  {
    mouthIndex += 1;
  }

  else if (isMouthAnimationPlayingBackwards == true)
  {
    mouthIndex -= 1;
  }
}

void resetRobotMouth() {
  mouthmatrix.clear();
  mouthmatrix.fillScreen(0xF800);
  mouthmatrix.drawBitmap(0, 0, mouthPos[1], 8, 5, 0xFFFF );
  mouthmatrix.show();

}


void flushSerial()
{
  while (Serial.available())
    Serial.read();
}

void handleSerialInput()
{
  if (BTLEserial.available() > 0)
  {
    timeSinceLastCmd = millis();
    incomingByte = BTLEserial.read();

    if (incomingByte == MOTOR_COMMAND_DELIMITER && currentState == NOTHING)
    {
      currentState = MOTOR;


      if (BTLEserial.available() > 0)
      {
        incomingByte = BTLEserial.read();
      }

      else
      {
        return;
      }

    }

    else if (incomingByte == MESSAGE_DELIMITER && currentState == NOTHING)
    {
      currentState = MESSAGE;

      if (BTLEserial.available() > 0)
      {
        incomingByte = BTLEserial.read();
      }

      else
      {
        return;
      }

    }
    /*
    else if (incomingByte == PIN_TOGGLE_DELIMITER && currentState == NOTHING)
    {
     currentState = TOGGLE;
     return;
    }

    else if (incomingByte == PIN_PWM_DELIMITER && currentState == NOTHING)
    {
     currentState = PINPWM;
     return;
    }
    */
    if (currentState == MOTOR)
    {

      if (motorCmdIndex <= 4)
      {
        motorCommand[motorCmdIndex] = incomingByte;
        motorCmdIndex++;
      }

      else if (motorCmdIndex >= 5)
      {
        handleRobotMovement(incomingByte);
      }
    }

    else if (currentState == MESSAGE)
    {
      handleRobotIncomingMessage(incomingByte);
    }
  }

  else
  {
    if (currentCmdTime - timeSinceLastCmd > 1000)
    {
      currentState = NOTHING;
      runMotor(0, MOTOR_RELEASE, leftMotor);
      runMotor(0, MOTOR_RELEASE, rightMotor);
    }
  }
}




void handleRobotMovement(byte incomingByte)
{
  byte leftDirection = motorCommand[0];
  byte leftSpeed = motorCommand[1] * 2;

  byte rightDirection = motorCommand[2];
  byte rightSpeed = motorCommand[3] * 2;
  runMotor(leftSpeed, leftDirection, leftMotor);

  runMotor(rightSpeed, rightDirection, rightMotor);

  byte servoMovement = motorCommand[4];

  if (servoMovement != SERVO_NOTHING)
  {
    moveServo(servoMovement);
  }
  motorCmdIndex = 0;
  currentState = NOTHING;

}

void handleRobotIncomingMessage(byte incomingByte)
{
  if ((char)incomingByte != '\n' && robotMessageIndex < 38)
  {
    robotMessage[robotMessageIndex] = (char)incomingByte;
    robotMessageIndex++;

  }
  else
  {
    // null-terminated string
    robotMessage[robotMessageIndex + 1] = '\0';
    String messageAsString(robotMessage);
    //mouthSerial.println("$$$ALL,OFF");
    Serial.print('S');
    Serial.print(messageAsString);
    Serial.print('\n');
    messageStartTime = millis();
    messageEstimatedTime = messageAsString.length() * 150;
    isTransmittingMessage = true;
    currentState = NOTHING;
    robotMessageIndex = 0;
    // most efficient way to reallocate a char array to null.
    memset(&robotMessage[0], 0, sizeof(robotMessage));
  }
}

void runMotor(byte motorSpeed, byte motorDirection, Adafruit_DCMotor *motor)
{
  motor->setSpeed(motorSpeed);

  switch (motorDirection) {
    case MOTOR_FORWARD:
      motor->run(FORWARD);
      break;
    case MOTOR_BACKWARD:
      motor->run(BACKWARD);
      break;
    case MOTOR_RELEASE:
      motor->run(RELEASE);
      break;
  }
}


void moveServo(byte servoDirection)
{

  if (servoDirection == SERVO_DOWN && topPos > topPosMin)
  {
    topPos = topPos - topServoIncrement;
  }

  else if (servoDirection == SERVO_UP && topPos <= topPosMax)
  {
    topPos = topPos + topServoIncrement;
  }

  else if (servoDirection == SERVO_LEFT && bottomPos <= bottomPosMax)
  {
    bottomPos = bottomPos + bottomServoIncrement;
  }

  else if (servoDirection == SERVO_RIGHT && bottomPos >= bottomPosMin)
  {
    bottomPos = bottomPos - bottomServoIncrement;
  }

  else if (servoDirection == SERVO_DOWN_RIGHT && topPos > topPosMin && bottomPos >= bottomPosMin)
  {
    topPos = topPos - topServoIncrement;
    bottomPos = bottomPos - bottomServoIncrement;
  }

  else if (servoDirection == SERVO_DOWN_LEFT && topPos > topPosMin && bottomPos <= bottomPosMax)
  {
    topPos = topPos - topServoIncrement;
    bottomPos = bottomPos + bottomServoIncrement;
  }

  else if (servoDirection == SERVO_UP_RIGHT && topPos <= topPosMax && bottomPos >= bottomPosMin)
  {
    bottomPos = bottomPos - bottomServoIncrement;
    topPos = topPos + topServoIncrement;
  }

  else if (servoDirection == SERVO_UP_LEFT && topPos <= topPosMax && bottomPos <= bottomPosMax)
  {
    bottomPos = bottomPos + bottomServoIncrement;
    topPos = topPos + topServoIncrement;
  }

  bottomServo.write(bottomPos);
  topServo.write(topPos);
}


