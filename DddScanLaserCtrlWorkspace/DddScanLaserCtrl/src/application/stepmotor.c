#include <string.h>
#include "global.h"
#include "stepmotor.h"

#define MOTOR_NUM 2

typedef struct {
	bool pulse;
	short curPos;
	short stepsRemain;
} STEPM_DEF;

static STEPM_DEF stepm_list[MOTOR_NUM];
void TIME3_IT_Handler() {
	TIM_ClearITPendingBit(TIM3, TIM_IT_Update);
	for(int i = 0; i < MOTOR_NUM; i++) {
		if(stepm_list[i].stepsRemain == 0) continue;

		bool pulse = stepm_list[i].pulse;
		if(pulse) {
			if(stepm_list[i].stepsRemain < 0) stepm_list[i].stepsRemain++;
			else stepm_list[i].stepsRemain--;
		}
		switch(i) {
			case STEPMOTOR_RED_LASER_INDX:
				if(pulse) RED_LASER_STEP_STEP_PORT->BRR = RED_LASER_STEP_STEP_PIN;
				else RED_LASER_STEP_STEP_PORT->BSRR = RED_LASER_STEP_STEP_PIN;
				break;
			case STEPMOTOR_GREEN_LASER_INDX:
				if(pulse) GREEN_LASER_STEP_STEP_PORT->BRR = GREEN_LASER_STEP_STEP_PIN;
				else GREEN_LASER_STEP_STEP_PORT->BSRR = GREEN_LASER_STEP_STEP_PIN;
				break;
		}
		stepm_list[i].pulse = !stepm_list[i].pulse;
 	}
}

void stepMotorStop(uint8_t indx) {
	switch(indx) {
		case STEPMOTOR_RED_LASER_INDX:
			RED_LASER_STEP_RST_PORT->BRR = RED_LASER_STEP_RST_PIN;
			RED_LASER_STEP_STEP_PORT->BRR = RED_LASER_STEP_STEP_PIN; // low
			break;
		case STEPMOTOR_GREEN_LASER_INDX:
			GREEN_LASER_STEP_RST_PORT->BRR = GREEN_LASER_STEP_RST_PIN;
			GREEN_LASER_STEP_STEP_PORT->BRR = GREEN_LASER_STEP_STEP_PIN; // low
			break;
	}
	stepm_list[indx].pulse = FALSE;
	stepm_list[indx].curPos = 0;
	stepm_list[indx].stepsRemain = 0;
}


void stepMotorInit() {
	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_2MHz;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;

	GPIO_InitStructure.GPIO_Pin = RED_LASER_STEP_RST_PIN;  GPIO_Init(RED_LASER_STEP_RST_PORT, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = RED_LASER_STEP_STEP_PIN; GPIO_Init(RED_LASER_STEP_STEP_PORT, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = RED_LASER_STEP_DIR_PIN;  GPIO_Init(RED_LASER_STEP_DIR_PORT, &GPIO_InitStructure);

	GPIO_InitStructure.GPIO_Pin = GREEN_LASER_STEP_RST_PIN;	 GPIO_Init(GREEN_LASER_STEP_RST_PORT, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = GREEN_LASER_STEP_STEP_PIN; GPIO_Init(GREEN_LASER_STEP_STEP_PORT, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = GREEN_LASER_STEP_DIR_PIN;  GPIO_Init(GREEN_LASER_STEP_DIR_PORT, &GPIO_InitStructure);
	for(int i = 0; i < MOTOR_NUM; i++) stepMotorStop(i);

	RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM3, ENABLE);
	TIM_TimeBaseInitTypeDef  TIM_TimeBaseStructure;
	TIM_TimeBaseStructInit(&TIM_TimeBaseStructure);
	TIM_TimeBaseStructure.TIM_Prescaler = 720-1; // 72000kHz/720 - 100kHz
	TIM_TimeBaseStructure.TIM_Period = 20; //5kHz
	TIM_TimeBaseStructure.TIM_CounterMode = TIM_CounterMode_Up;
	TIM_TimeBaseInit(TIM3, &TIM_TimeBaseStructure);
	TIM_ARRPreloadConfig(TIM3, ENABLE);
	TIM_Cmd(TIM3, ENABLE);
	NVIC_PriorityGroupConfig(NVIC_PriorityGroup_2);
	NVIC_InitTypeDef NVIC_InitStructure;
	NVIC_InitStructure.NVIC_IRQChannel=TIM3_IRQn;
	NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 0;
	NVIC_InitStructure.NVIC_IRQChannelSubPriority = 0;
	NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
	NVIC_Init(&NVIC_InitStructure);
	TIM_ITConfig(TIM3, TIM_IT_Update, ENABLE);
}

void stepMotorStart(uint8_t indx, short steps) {
	switch(indx) {
		case STEPMOTOR_RED_LASER_INDX:
			RED_LASER_STEP_RST_PORT->BSRR = RED_LASER_STEP_RST_PIN; // reset mode off
			if(steps > 0) RED_LASER_STEP_DIR_PORT->BRR = RED_LASER_STEP_DIR_PIN;
			else RED_LASER_STEP_DIR_PORT->BSRR = RED_LASER_STEP_DIR_PIN;
			break;
		case STEPMOTOR_GREEN_LASER_INDX:
			GREEN_LASER_STEP_RST_PORT->BSRR = GREEN_LASER_STEP_RST_PIN;
			if(steps > 0) GREEN_LASER_STEP_DIR_PORT->BRR = GREEN_LASER_STEP_DIR_PIN;
			else GREEN_LASER_STEP_DIR_PORT->BSRR = GREEN_LASER_STEP_DIR_PIN;
			break;
	}
	delayMs(1);
	stepm_list[indx].stepsRemain = steps*K_STEPS;
}

short stepMotorGetRemains(uint8_t indx) {
	return stepm_list[indx].stepsRemain;
}

void stepMotorsSpeed(uint16_t period) {
	TIM_SetAutoreload(TIM3, period);
}

