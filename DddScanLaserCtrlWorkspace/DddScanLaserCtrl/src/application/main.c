#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include "global.h"
#include "USART_io.h"
#include "stepmotor.h"
#include "TSOP_IR.h"

const char *version = "v1.0 " __DATE__ " "__TIME__;

uint8_t cmdData[256], cmdDataSz;

#define  CMD_RQ_VERSION (0x2E)
#define  CMD_RESP_VERSION (0x2E)
#define  CMD_RESP_ERR_MSG (0x2F)
#define  CMD_RQ_SCAN_INIT_LASER_POSITION (0x10)
#define  CMD_RESP_SCAN_INIT_LASER_POSITION (0x20)
#define  CMD_RQ_SCAN_DO_STEP (0x11)
#define  CMD_RESP_SCAN_DO_STEP (0x21)
#define  CMD_RQ_LASER_CTRL (0x12)
#define  CMD_RESP_LASER_CTRL (0x22)
#define  CMD_RQ_DO_STEPS (0x13)
#define  CMD_RESP_DO_STEPS (0x23)
#define  CMD_RQ_MOTOR_POWER_OFF (0x14)
#define  CMD_RESP_MOTOR_POWER_OFF (0x24)

static void initPosition(uint16_t cntRed, uint16_t cntGreen) {
	RED_LASER_CTRL_PORT->BSRR = RED_LASER_CTRL_PIN;
	GREEN_LASER_CTRL_PORT->BSRR = GREEN_LASER_CTRL_PIN;

	stepMotorStart(STEPMOTOR_RED_LASER_INDX, 2000);
	stepMotorStart(STEPMOTOR_GREEN_LASER_INDX, -2000);
	while(stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX) != 0 || stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX) != 0) {
		if((RED_LASER_STEP_SENSOR_PORT->IDR & RED_LASER_STEP_SENSOR_PIN) == 0) {
			stepMotorStop(STEPMOTOR_RED_LASER_INDX);
			//DBG_printf("SENSOR RED (G:%d)\n", stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX));
		}
		if((GREEN_LASER_STEP_SENSOR_PORT->IDR & GREEN_LASER_STEP_SENSOR_PIN) == 0) {
			stepMotorStop(STEPMOTOR_GREEN_LASER_INDX);
			//DBG_printf("SENSOR GREEN (R:%d)\n", stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX));
		}
	}
	stepMotorStart(STEPMOTOR_RED_LASER_INDX, -cntRed);
	stepMotorStart(STEPMOTOR_GREEN_LASER_INDX, cntGreen);
	while(stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX) != 0 &&
			stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX) != 0) {
	}
	stepMotorStop(STEPMOTOR_RED_LASER_INDX);
	stepMotorStop(STEPMOTOR_GREEN_LASER_INDX);
}

static void execBluetoothInterface() {
	int cmd = CI_getLastCmdCode();
	if(cmd == CMD_ST_NO_CMD) return;

	switch(cmd) {
	 case CMD_ST_WRONG_DATA:
		 break;
	 case CMD_ST_TIMEOUT_DATA:
		 DBG_puts("CMD_ST_TIMEOUT_DATA\n");
		 break;
//=====================================================================
	 case CMD_RQ_VERSION:
		 DBG_puts("CMD_RQ_VERSION\n");
		 CI_getCmdBody(NULL, 0);
		 CI_putCmd(CMD_RESP_VERSION, (uint8_t*)version, (uint8_t)strlen(version));
		 break;
	 case CMD_RQ_SCAN_INIT_LASER_POSITION:
		 DBG_puts("CMD_RQ_SCAN_INIT_LASER_POSITION start\n");
		 CI_getCmdBody(cmdData, 4);
		 initPosition(((uint16_t)cmdData[0] << 8) | (uint16_t)cmdData[1], ((uint16_t)cmdData[2] << 8) | (uint16_t)cmdData[3]);
		 DBG_puts("CMD_RQ_SCAN_INIT_LASER_POSITION finish\n");
		 CI_putCmd(CMD_RESP_SCAN_INIT_LASER_POSITION, NULL, 0);
		 break;
	 case CMD_RQ_SCAN_DO_STEP:
		 DBG_puts("CMD_RQ_SCAN_DO_STEP\n");
		 CI_getCmdBody(cmdData, 1);
		 if(cmdData[0] != 0) CI_putCmd(CMD_RESP_SCAN_DO_STEP, NULL, 0);
		 // make step after response
		 stepMotorStart(STEPMOTOR_RED_LASER_INDX, -1);
		 stepMotorStart(STEPMOTOR_GREEN_LASER_INDX, 1);
		 while(stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX) != 0 &&
				 stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX) != 0) {
		 }
		 if(cmdData[0] == 0) CI_putCmd(CMD_RESP_SCAN_DO_STEP, NULL, 0);
		 DBG_puts("CMD_RQ_SCAN_DO_STEP finish\n");
		 break;
	 case CMD_RQ_DO_STEPS:
		 DBG_puts("CMD_RQ_DO_STEPS\n");
		 CI_getCmdBody(cmdData, 2);
		 uint8_t dRed = cmdData[0];
		 uint8_t dGreen = cmdData[1];
		 if(dRed != 0) stepMotorStart(STEPMOTOR_RED_LASER_INDX, dRed == 1 ? 1:-1);
		 if(dGreen != 0) stepMotorStart(STEPMOTOR_GREEN_LASER_INDX, dGreen == 1 ? 1:-1);
		 while(stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX) != 0 &&
				 stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX) != 0) {
		 }
		 DBG_puts("CMD_RQ_DO_STEPS finish\n");
		 CI_putCmd(CMD_RESP_DO_STEPS, NULL, 0);
		 break;
	 case CMD_RQ_LASER_CTRL:
		 CI_getCmdBody(cmdData, 3);
		 DBG_printf("CMD_RQ_LASER_CTRL %x\n", cmdData[0]);
		 if((cmdData[0] & 0x01) != 0) RED_LASER_CTRL_PORT->BSRR = RED_LASER_CTRL_PIN;
		 else  RED_LASER_CTRL_PORT->BRR = RED_LASER_CTRL_PIN;
		 if((cmdData[0] & 0x02) != 0) GREEN_LASER_CTRL_PORT->BSRR = GREEN_LASER_CTRL_PIN;
		 else GREEN_LASER_CTRL_PORT->BRR = GREEN_LASER_CTRL_PIN;
		 delayMs(((uint16_t)cmdData[1] << 8) | (uint16_t)cmdData[2]);
		 CI_putCmd(CMD_RESP_LASER_CTRL, NULL, 0);
		 break;
	 case CMD_RQ_MOTOR_POWER_OFF:
		 DBG_puts("CMD_RQ_MOTOR_POWER_OFF\n");
		 CI_getCmdBody(NULL, 0);
	 	 stepMotorStop(STEPMOTOR_GREEN_LASER_INDX);
	 	 stepMotorStop(STEPMOTOR_RED_LASER_INDX);
		 CI_putCmd(CMD_RESP_MOTOR_POWER_OFF, NULL, 0);
		 break;
//=====================================================================
	 default:
		 CI_getCmdBody(NULL, 0);
		 char *str = USART_DBG_printf("unsupported CMD:%xh\n", cmd);
		 CI_putCmd(CMD_RESP_ERR_MSG, (uint8_t*)str, strlen(str));
		 break;
	}
}

static void execIrInterface() {
	int16_t sz;
#ifdef IR_TEST
	uint16_t *cmdData = TSOP_getRawData(&sz);
	if(sz > 0) {
		DBG_printf("\nIR[%d]:", sz/2);
		for(int i = 0; i < sz; i+=2) {
			DBG_printf("  %d,%d", cmdData[i], cmdData[i+1]);
		}
	}
#else
	uint8_t cmd;
	uint8_t *cmdData = TSOP_getCmd(&cmd, &sz);
	if(cmd == CMD_IR_NO_CMD) return;

	if(sz > 0) {
		DBG_printf("\n<----IR[%d](%d):", cmd, sz);
		for(int i = 0; i < sz; i++) DBG_printf("  %d", cmdData[i]);
	} else DBG_printf("\n<----IR[%d]", cmd);

	switch(cmd) {
	 case CMD_IR_LASERS_OFF:
		 DBG_puts("CMD_IR_LASERS_OFF\n");
		 RED_LASER_CTRL_PORT->BRR = RED_LASER_CTRL_PIN;
		 GREEN_LASER_CTRL_PORT->BRR = GREEN_LASER_CTRL_PIN;
		 break;
	 case CMD_IR_LASERS_ON:
		 DBG_puts("CMD_IR_LASERS_ON\n");
		 RED_LASER_CTRL_PORT->BSRR = RED_LASER_CTRL_PIN;
		 GREEN_LASER_CTRL_PORT->BSRR = GREEN_LASER_CTRL_PIN;
		 break;
	 case CMD_IR_MOTORS_OFF:
		 DBG_puts("CMD_IR_MOTORS_OFF\n");
	 	 stepMotorStop(STEPMOTOR_GREEN_LASER_INDX);
	 	 stepMotorStop(STEPMOTOR_RED_LASER_INDX);
		 break;
	 case CMD_IR_GOTO_TEST_POSITION:
	 case CMD_IR_GOTO_START:
		 DBG_puts("CMD_IR_GOTO_.. start\n");
		 initPosition(((uint16_t)cmdData[0] << 8) | (uint16_t)cmdData[1], ((uint16_t)cmdData[2] << 8) | (uint16_t)cmdData[3]);
		 break;
	 case CMD_IR_DO_STEP:
		 DBG_puts("CMD_IR_DO_STEP\n");
		 stepMotorStart(STEPMOTOR_RED_LASER_INDX, -1);
		 stepMotorStart(STEPMOTOR_GREEN_LASER_INDX, 1);
		 while(stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX) != 0 &&
				 stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX) != 0) {
		 }
		 DBG_puts("CMD_IR_DO_STEP finish\n");
		 break;
	 case CMD_IR_STEP_RED_RIGHT:
		 DBG_puts("CMD_IR_STEP_RED_RIGHT\n");
		 stepMotorStart(STEPMOTOR_RED_LASER_INDX, -1);
		 while(stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX) != 0) {}
		 break;
	 case CMD_IR_STEP_RED_LEFT:
		 DBG_puts("CMD_IR_STEP_RED_LEFT\n");
		 stepMotorStart(STEPMOTOR_RED_LASER_INDX, 1);
		 while(stepMotorGetRemains(STEPMOTOR_RED_LASER_INDX) != 0) {}
		 break;
	 case CMD_IR_STEP_GREEN_RIGHT:
		 DBG_puts("CMD_IR_STEP_GREEN_RIGHT\n");
		 stepMotorStart(STEPMOTOR_GREEN_LASER_INDX, 1);
		 while(stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX) != 0) {}
		 break;
	 case CMD_IR_STEP_GREEN_LEFT:
		 DBG_puts("CMD_IR_STEP_GREEN_LEFT\n");
		 stepMotorStart(STEPMOTOR_GREEN_LASER_INDX, -1);
		 while(stepMotorGetRemains(STEPMOTOR_GREEN_LASER_INDX) != 0) {}
		 break;
	 default:
		 DBG_printf("\nUnknowd cmd:%d\n", cmd);
	}
		//delayMs(100);
#endif
}

int main() {
	_led_period = LED_PERIOD_NO_ERROR;
	SystemStartup();
	USART_init();
	TSOP_init();
	DBG_printf("Started %s\n", version);
	stepMotorInit();

	while(1) {
		execBluetoothInterface();
		//execIrInterface();
	}
}


