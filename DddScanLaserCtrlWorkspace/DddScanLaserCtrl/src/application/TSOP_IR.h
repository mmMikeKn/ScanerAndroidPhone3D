#ifndef TSOP_IR_H_
#define TSOP_IR_H_

//#define IR_TEST
// TSOP1736
//TIM4 + PB6 (5V FT) - IR PWMI TIM4_CH1
void TSOP_init();

#ifdef IR_TEST
uint16_t *TSOP_getRawData(int16_t *out_sz);
#else

#define CMD_IR_LASERS_OFF 0
#define CMD_IR_LASERS_ON 1
#define CMD_IR_MOTORS_OFF 2
#define CMD_IR_GOTO_START 3
#define CMD_IR_GOTO_TEST_POSITION 4
#define CMD_IR_DO_STEP 5
#define CMD_IR_STEP_RED_RIGHT 6
#define CMD_IR_STEP_RED_LEFT 7
#define CMD_IR_STEP_GREEN_RIGHT 8
#define CMD_IR_STEP_GREEN_LEFT 9
#define CMD_IR_NO_CMD 0xFF

// 5000
#define PATTERN_HD_T_MIN 4800
#define PATTERN_HD_T_MAX 5500

// 500
#define PATTERN_HD_S_MIN 300
#define PATTERN_HD_S_MAX 600

// 300
#define PATTERN_BIT_T_MIN 210
#define PATTERN_BIT_T_MAX 450

//300
#define PATTERN_BIT_S0_MIN 200
#define PATTERN_BIT_S0_MAX 450

#define PATTERN_BIT_S1_MIN 470
#define PATTERN_BIT_S1_MAX 690

uint8_t *TSOP_getCmd(uint8_t *cmd, int16_t *out_sz);
#endif

void TIME4_IT_Handler();

#endif /* TSOP_IR_H_ */
