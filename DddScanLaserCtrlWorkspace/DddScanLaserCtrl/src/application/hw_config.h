#ifndef __HW_CONFIG_H
#define __HW_CONFIG_H

void SystemStartup(void);
void Setup0_GPIO();
void StartDMA_ADC();
void Setup2_IRQ();
void Setup3_ADC();
void Setup4_Timers();


void Enter_LowPowerMode(void);
void Leave_LowPowerMode(void);
void Reset_Device(void);

extern RCC_ClocksTypeDef RCC_Clocks ;

#endif  /*__HW_CONFIG_H*/
