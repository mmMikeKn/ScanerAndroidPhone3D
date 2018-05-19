#ifndef STEPMOTOR_H_
#define STEPMOTOR_H_

#define STEPMOTOR_RED_LASER_INDX 0
#define STEPMOTOR_GREEN_LASER_INDX 1

#define K_STEPS 8

// 35BYGHM302-06LA 0.3A, 0.9°, 12Â
// A9488 Rcs = 0.1Om  I= 0.3A   Vref=8 * 0.3 * 0.1 = 0.24V

void stepMotorInit();
void stepMotorStart(uint8_t indx, short steps);
void stepMotorStop(uint8_t indx);
void stepMotorsSpeed(uint16_t period);
short stepMotorGetRemains(uint8_t indx);

void TIME3_IT_Handler();

#endif /* STEPMOTOR_H_ */
