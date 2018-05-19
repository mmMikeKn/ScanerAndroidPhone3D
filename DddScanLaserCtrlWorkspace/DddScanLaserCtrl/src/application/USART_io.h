#ifndef USART_IO_H_
#define USART_IO_H_

//------------------- for stm32f10x_it.h
void USART2_IT_RxReady_exec(uint8_t c);
void USART2_IT_TxReady_exec();
void USART1_IT_RxReady_exec(uint8_t c);
void USART1_IT_TxReady_exec();
//--------------------

//======================================
#define  CMD_ST_NO_CMD (-1)
#define  CMD_ST_WRONG_DATA (-2)
#define  CMD_ST_TIMEOUT_DATA (-3)

void USART_init();
void CI_putCmd(uint8_t code, const uint8_t *data, int sz);
short CI_getLastCmdCode(void);
uint8_t CI_getCmdBody(uint8_t *data, int maxSz);

//======================================
#define USART_DBG_TX_BUFFER_SZ 2048
void USART_DBG_putc(char c);
void USART_DBG_puts(char *str);
void USART_DBG_hexDump(uint8_t *bin, uint8_t len);
void USART_DBG_bin(uint8_t *bin, uint16_t len);
char *USART_DBG_printf(const char* str, ...);
//======================================
#endif
