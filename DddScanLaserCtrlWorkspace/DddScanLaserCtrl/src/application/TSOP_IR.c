#include "stm32f10x.h"
#include "stm32f10x_conf.h"
#include <string.h>

#include "TSOP_IR.h"

#include "global.h"
#include "USART_io.h"

//#define TSOP_DEBUG

void TSOP_init() {
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_GPIOB, ENABLE);
	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_IN_FLOATING;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_6;
	GPIO_Init(GPIOB, &GPIO_InitStructure);

	RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM4, ENABLE);
	TIM_TimeBaseInitTypeDef  TIM_TimeBaseStructure;
	TIM_TimeBaseStructInit(&TIM_TimeBaseStructure);
	TIM_TimeBaseStructure.TIM_Prescaler = 72 - 1;
	TIM_TimeBaseInit(TIM4, &TIM_TimeBaseStructure);

	TIM_ICInitTypeDef TIM_ICStructure;
	TIM_ICStructure.TIM_Channel = TIM_Channel_1;
	TIM_ICStructure.TIM_ICPolarity = TIM_ICPolarity_Falling;
	TIM_ICStructure.TIM_ICSelection = TIM_ICSelection_DirectTI;
	TIM_ICStructure.TIM_ICPrescaler = TIM_ICPSC_DIV1;
	TIM_ICStructure.TIM_ICFilter = 0;
	TIM_PWMIConfig(TIM4, &TIM_ICStructure);

	TIM_SelectInputTrigger(TIM4, TIM_TS_TI1FP1);
	TIM_SelectSlaveMode(TIM4, TIM_SlaveMode_Reset);
	TIM_SelectMasterSlaveMode(TIM4, TIM_MasterSlaveMode_Enable);

#ifdef IR_TEST
    TIM_OCInitTypeDef TIM_OCStructure;
    TIM_OCStructure.TIM_OCMode = TIM_OCMode_Timing;
    TIM_OCStructure.TIM_OutputState = TIM_OutputState_Disable;
    TIM_OCStructure.TIM_OutputNState = TIM_OutputNState_Disable;
    TIM_OCStructure.TIM_Pulse = 15000;
    TIM_OC3Init(TIM4, &TIM_OCStructure);
    TIM_ITConfig(TIM4, TIM_IT_CC3, ENABLE);
    TIM_ClearFlag(TIM4, TIM_FLAG_CC3);
#endif

    TIM_ITConfig(TIM4, TIM_IT_CC1, ENABLE);
    TIM_ClearFlag(TIM4, TIM_FLAG_CC1);

    TIM_Cmd(TIM4, ENABLE);
    NVIC_EnableIRQ(TIM4_IRQn);
}

#ifdef IR_TEST
#define MAX_DATA_SZ 256
static volatile  uint16_t buf[MAX_DATA_SZ];
static volatile int16_t sz = 0;
enum {
	IDLE,
	RECORD,
	FINISH
};
static volatile uint8_t state = IDLE;
uint16_t *TSOP_getRawData(int16_t *out_sz) {
	static uint16_t resp_buf[MAX_DATA_SZ];
	NVIC_DisableIRQ(TIM4_IRQn);
	if(sz > 0 && state == FINISH) {
		memcpy((void*)resp_buf, (void*)buf, sz*sizeof(uint16_t));
		*out_sz = sz; sz = 0;
		state = IDLE;
	} else {
		*out_sz = 0;
	}
	NVIC_EnableIRQ(TIM4_IRQn);
	return (uint16_t*)resp_buf;
}
#else

#define MAX_BODY_SZ 4
enum {
	IDLE,
	RCV_CMD,
	RCV_BODY,
	FINISH
};

static volatile uint8_t rcv_cmd;
static volatile uint8_t state = IDLE;
static volatile  uint8_t buf[MAX_BODY_SZ];
static volatile int8_t sz = 0, wait_sz, bits_cnt = 0;
static volatile int16_t cur_byte;

uint8_t *TSOP_getCmd(uint8_t *cmd, int16_t *out_sz) {
	static uint8_t resp_buf[MAX_BODY_SZ];
	NVIC_DisableIRQ(TIM4_IRQn);
	if(state == FINISH) {
		*cmd = rcv_cmd;
		if(sz > 0) memcpy((void*)resp_buf, (void*)buf, sz);
		*out_sz = sz; sz = 0;
		state = IDLE;
	} else {
		*out_sz = 0;
		*cmd = CMD_IR_NO_CMD;
	}
	NVIC_EnableIRQ(TIM4_IRQn);
	return (uint8_t*)resp_buf;
}

static void parse(uint16_t transmit_time, uint16_t silent_time) {
#ifdef TSOP_DEBUG
    DBG_printf("\nIR raw: %d-%d", transmit_time, silent_time);
#endif
    if(transmit_time > PATTERN_HD_T_MIN && transmit_time < PATTERN_HD_T_MAX &&
    		silent_time < PATTERN_HD_S_MAX && silent_time > PATTERN_HD_S_MIN) {
    	state = RCV_CMD; bits_cnt = 0; cur_byte = 0;
#ifdef TSOP_DEBUG
    	DBG_printf("\nIR RCV HD %d/%d", transmit_time, silent_time);
#endif
    	return;
    }
    if(state != RCV_CMD && state != RCV_BODY) return;
    if(transmit_time < PATTERN_BIT_T_MIN || transmit_time > PATTERN_BIT_T_MAX) {
    	DBG_printf("\nIR bit transmit_time ERROR [%d] %d/%d", state, transmit_time, silent_time);
    	state = IDLE;
    	return;
    }
    bool bitValue1 = silent_time < PATTERN_BIT_S1_MAX && silent_time > PATTERN_BIT_S1_MIN;
    bool bitValue0 = silent_time < PATTERN_BIT_S0_MAX && silent_time > PATTERN_BIT_S0_MIN;
    if(!bitValue1 && !bitValue0) {
    	DBG_printf("\nIR bit silent_time ERROR [%d] %d/%d", state, transmit_time, silent_time);
		state = IDLE;
		return;
    }
    if(bitValue1) cur_byte |= 1 << bits_cnt;
    bits_cnt++;
#ifdef TSOP_DEBUG
	DBG_printf("\nIR CUR BYTE %x (%d):%d", cur_byte, bits_cnt, bitValue1);
#endif

    if(state == RCV_CMD && bits_cnt > 4) {
    	rcv_cmd = cur_byte;
    	uint8_t chk = 0;
    	for(uint8_t i = 0; i < 5; cur_byte = cur_byte >> 1, i++)
    		if((cur_byte & 1) != 0)
    			chk++;
    	if((chk & 1) != 0) {
        	DBG_printf("\nIR CMD control bit ERROR [%d] %x", state, cur_byte);
    		state = IDLE;
    		return;
    	}
    	rcv_cmd &= 0x0F;
#ifdef TSOP_DEBUG
    	DBG_printf("\nIR CMD:%d", rcv_cmd);
#endif
    	if(rcv_cmd == CMD_IR_GOTO_START || rcv_cmd == CMD_IR_GOTO_TEST_POSITION) {
    		state = RCV_BODY; bits_cnt = 0; cur_byte = 0; sz = 0; wait_sz = 4;
    	} else {
    		state = FINISH;
    	}
    }
    if(state == RCV_BODY && bits_cnt > 8) {
    	uint8_t chk = 0;
    	uint8_t tmp = (uint8_t)cur_byte;
    	for(uint8_t i = 0; i < 9; cur_byte = cur_byte >> 1, i++)
    		if((cur_byte & 1) != 0)
    			chk++;
    	if((chk & 1) != 0) {
        	DBG_printf("\nIR BODY control bit ERROR [%d:%d] %x", state, sz, cur_byte);
    		state = IDLE;
    		return;
    	}
#ifdef TSOP_DEBUG
    	DBG_printf("\nIR BODY[%d]:%d", sz, (uint8_t)tmp);
#endif
    	buf[sz++] = (uint8_t)tmp;
    	bits_cnt = 0; cur_byte = 0;
    	if(sz >= wait_sz) {
    		state = FINISH;
    	}
    }
}

#endif


void TIME4_IT_Handler() {
	if (TIM_GetITStatus(TIM4, TIM_IT_CC1) != RESET) {
        TIM_ClearITPendingBit(TIM4, TIM_IT_CC1);
        uint16_t period = TIM_GetCapture1(TIM4);
        uint16_t cycle = TIM_GetCapture2(TIM4);
        if (TIM_GetFlagStatus(TIM3, TIM_FLAG_CC1OF) != RESET) {
        	TIM_ClearFlag(TIM3, TIM_FLAG_CC1OF);
        	period = 60000;
        	cycle = 20000;
        }
#ifdef IR_TEST
        if(state == IDLE) state = RECORD;
        if(sz < MAX_DATA_SZ && state == RECORD) {
        	//DBG_printf(" %d/%d", period, cycle);
        	buf[sz++] = cycle;
        	buf[sz++] = period-cycle;
        }
#else
        uint16_t transmit_time = cycle;
        uint16_t silent_time = period-cycle;
        parse(transmit_time, silent_time);
#endif
	}
#ifdef IR_TEST
    if (TIM_GetITStatus(TIM4, TIM_IT_CC3) != RESET) {
            TIM_ClearITPendingBit(TIM4, TIM_IT_CC3);
            if(state == RECORD) state = FINISH;
            //DBG_puts("\nCC3\n");
    }
#endif
}

