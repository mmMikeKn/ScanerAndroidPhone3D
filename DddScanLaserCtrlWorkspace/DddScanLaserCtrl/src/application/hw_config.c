#include <string.h>
#include <stdarg.h>

#include "global.h"
#include "core_cm3.h"
#include "stm32f10x_spi.h"

ErrorStatus HSEStartUpStatus ;
RCC_ClocksTypeDef RCC_Clocks ;

#ifdef  __cplusplus
extern "C" {
#endif
uint32_t GetCpuClock()
  {
    return RCC_Clocks.SYSCLK_Frequency ;
  }
#ifdef  __cplusplus
  }
#endif

void SystemStartup(void) {
 /* Unlock the internal flash */
	FLASH_Unlock();

 /* RCC system reset(for debug purpose) */
	RCC_DeInit();

 /* Enable HSE */
	RCC_HSEConfig(RCC_HSE_ON);

 /* Wait till HSE is ready */
	HSEStartUpStatus = RCC_WaitForHSEStartUp();

	if (HSEStartUpStatus == SUCCESS) {
		FLASH_PrefetchBufferCmd(FLASH_PrefetchBuffer_Enable);/* Enable Prefetch Buffer */
		FLASH_SetLatency(FLASH_Latency_2);/* Flash 2 wait state */
		RCC_HCLKConfig(RCC_SYSCLK_Div1);/* HCLK = SYSCLK */
		RCC_PCLK2Config(RCC_HCLK_Div1);/* PCLK2 = HCLK */
		RCC_PCLK1Config(RCC_HCLK_Div2); /* PCLK1 = HCLK/2 */
		RCC_ADCCLKConfig(RCC_PCLK2_Div6);     //ADCCLK = PCLK2/6 = 12MHz
		RCC_PLLConfig(RCC_PLLSource_HSE_Div1, RCC_PLLMul_9 );/* PLLCLK = 8MHz * 9 = 72 MHz */
		RCC_PLLCmd(ENABLE); /* Enable PLL */
		   /* Wait till PLL is ready */
		while (RCC_GetFlagStatus(RCC_FLAG_PLLRDY) == RESET);

		RCC_SYSCLKConfig(RCC_SYSCLKSource_PLLCLK);/* Select PLL as system clock source */

   /* Wait till PLL is used as system clock source */
		while (RCC_GetSYSCLKSource() != 0x08);
	}
	RCC_GetClocksFreq( &RCC_Clocks ) ;
	SysTick_Config(SystemFrequency / 1000);

	RCC_APB2PeriphClockCmd(RCC_APB2Periph_AFIO|RCC_APB2Periph_GPIOA|RCC_APB2Periph_GPIOB|RCC_APB2Periph_GPIOC, ENABLE);
	 //----------------------
	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_10MHz;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_OD;
	GPIO_InitStructure.GPIO_Pin = DBG_LED_PIN; GPIO_Init(DBG_LED_PORT, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = DBG_EXT_LED_PIN; GPIO_Init(DBG_EXT_LED_PORT, &GPIO_InitStructure);

	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_InitStructure.GPIO_Pin = RED_LASER_CTRL_PIN; GPIO_Init(RED_LASER_CTRL_PORT, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = GREEN_LASER_CTRL_PIN; GPIO_Init(GREEN_LASER_CTRL_PORT, &GPIO_InitStructure);


	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_IPU;
	GPIO_InitStructure.GPIO_Pin = RED_LASER_STEP_SENSOR_PIN;   GPIO_Init(RED_LASER_STEP_SENSOR_PORT, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = GREEN_LASER_STEP_SENSOR_PIN; GPIO_Init(GREEN_LASER_STEP_SENSOR_PORT, &GPIO_InitStructure);

 //----------------------
	RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM2, ENABLE);
	TIM_TimeBaseInitTypeDef TIM_TimeBaseStructure;
	TIM_TimeBaseStructInit(&TIM_TimeBaseStructure);
	TIM_TimeBaseStructure.TIM_Prescaler = 7200-1;
	TIM_TimeBaseStructure.TIM_Period = 0xFFFF;
	TIM_TimeBaseStructure.TIM_CounterMode = TIM_CounterMode_Up;
	TIM_TimeBaseInit(TIM2, &TIM_TimeBaseStructure);
}

void delayMs(uint16_t msec)  {
	uint16_t delay = msec*10;
	TIM_Cmd(TIM2, ENABLE);
	TIM_SetCounter(TIM2, 0);
	while(TIM_GetCounter(TIM2) < delay);
	TIM_Cmd(TIM2, DISABLE);
}


