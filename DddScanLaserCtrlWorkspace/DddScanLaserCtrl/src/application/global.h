#ifndef GLOBAL_H_
#define GLOBAL_H_

#include "stm32f10x.h"
#include "stm32f10x_conf.h"
#include "stm32f10x_rtc.h"

#include "hw_config.h"

#define DEBUG_MODE
#ifdef DEBUG_MODE
 #define DBG_printf(...) { USART_DBG_printf(__VA_ARGS__); }
 #define DBG_puts(s) { USART_DBG_puts(s); }
 #define DBG_hexDump(bin, len) { USART_DBG_hexDump(bin, len); }
#else
 #define DBG_printf(...) {}
 #define DBG_puts(s) {}
 #define USART_DBG_hexDump(bin, len) {}
#endif


extern volatile uint32_t _sysTicks;
#define LED_PERIOD_NO_ERROR 1000
#define LED_PERIOD_CRITICAL_ERROR 200
extern volatile uint32_t _led_period;

void delayMs(uint16_t msec);

// USART1: TX->PA9 RX->PA10
// USART2: TX->PA2 RX->PA3
// DBG LED: PC13 - board, PC14 - external.
// TSOP1736: PB6
//                      RST   STEP  DIR   SENSOR
// PA11 - Red Laser,   PB15, PB13, PB12, PB14 - StepMotor
// PA6 - Green Laser,  PB11, PB0,  PB10, PA7  - StepMotor
//  --- stm32f103c8t6 TIM1..TIM4
// TIM4 + PB6 - IR PWMI TIM4_CH1
// TIM3 - step motor timer
// TIM2 - for timeout calculation. void delayMs(uint16_t msec)

#define DBG_LED_PIN GPIO_Pin_13
#define DBG_LED_PORT GPIOC
#define DBG_EXT_LED_PIN GPIO_Pin_14
#define DBG_EXT_LED_PORT GPIOC

#define RED_LASER_STEP_RST_PIN GPIO_Pin_15
#define RED_LASER_STEP_RST_PORT GPIOB
#define RED_LASER_STEP_STEP_PIN GPIO_Pin_13
#define RED_LASER_STEP_STEP_PORT GPIOB
#define RED_LASER_STEP_DIR_PIN GPIO_Pin_12
#define RED_LASER_STEP_DIR_PORT GPIOB

#define RED_LASER_STEP_SENSOR_PIN GPIO_Pin_14
#define RED_LASER_STEP_SENSOR_PORT GPIOB

#define RED_LASER_CTRL_PIN GPIO_Pin_11
#define RED_LASER_CTRL_PORT GPIOA

//-------------------------------------------
#define GREEN_LASER_STEP_RST_PIN GPIO_Pin_11
#define GREEN_LASER_STEP_RST_PORT GPIOB
#define GREEN_LASER_STEP_STEP_PIN GPIO_Pin_0
#define GREEN_LASER_STEP_STEP_PORT GPIOB
#define GREEN_LASER_STEP_DIR_PIN GPIO_Pin_10
#define GREEN_LASER_STEP_DIR_PORT GPIOB

#define GREEN_LASER_STEP_SENSOR_PIN GPIO_Pin_7
#define GREEN_LASER_STEP_SENSOR_PORT GPIOA

#define GREEN_LASER_CTRL_PIN GPIO_Pin_6
#define GREEN_LASER_CTRL_PORT GPIOA


#endif /* GLOBAL_H_ */
