#include <string.h>
#include <stdarg.h>

#include "stm32f10x.h"
#include "stm32f10x_conf.h"
#include <core_cm3.h>
#include <stm32f10x_usart.h>
#include <stm32f10x_rtc.h>
#include "USART_io.h"
#include "global.h"


//=========================================================================================
// USART2 Command Interface (CI)
static volatile uint8_t USART2_TxBuffer[256+4];
static volatile uint32_t USART2_TxBufferSz = 0, USART2_TxBufferPtr = 0;
static volatile uint8_t USART2_isTrnsmitEnd = TRUE;
static volatile uint8_t USART2_RxBuffer[512];
static volatile uint32_t USART2_RxBufferPtr = 0;
static volatile short recvCmdCode = CMD_ST_NO_CMD;

void USART2_IT_TxReady_exec() {
	USART_SendData(USART2, (uint16_t)USART2_TxBuffer[USART2_TxBufferPtr++]);
	if(USART2_TxBufferPtr >= USART2_TxBufferSz) {
		USART_ITConfig(USART2, USART_IT_TXE, DISABLE);
		USART2_isTrnsmitEnd = TRUE;
	}
}

#define STX 0x02

// STX[2] || code[1] || sz[1] || body[sz] || lrc
void CI_putCmd(uint8_t code, const uint8_t *data, int sz) {
	while(!USART2_isTrnsmitEnd) {};
	USART2_TxBuffer[0] = STX;
	USART2_TxBuffer[1] = code;
	USART2_TxBuffer[2] = sz;
	if(sz != 0 && data != NULL) {
	 memcpy((unsigned char*)USART2_TxBuffer+3, data, sz);
	}
	USART2_TxBuffer[3+sz] = 0;
	for(int i = 0; i < (sz+2); i++) USART2_TxBuffer[3+sz] ^= USART2_TxBuffer[1+i];
	USART2_TxBufferSz = sz+4; USART2_TxBufferPtr = 0; USART2_isTrnsmitEnd = FALSE;
	DBG_hexDump((uint8_t*)USART2_TxBuffer, USART2_TxBufferSz); DBG_puts(" snd\n");
	USART_ITConfig(USART2, USART_IT_TXE, ENABLE);
	while(!USART2_isTrnsmitEnd) {}
}

// STX || code[1] || sz[1] || body[sz] || lrc
void USART2_IT_RxReady_exec(uint8_t c) {
	if(USART2_RxBufferPtr == 0 && c != STX) return;
	USART2_RxBuffer[USART2_RxBufferPtr++] = c;

	if(USART2_RxBufferPtr > 3) {
		uint32_t crcOfs = USART2_RxBuffer[2] + 3;
		if(USART2_RxBufferPtr > crcOfs) {
			DBG_hexDump((uint8_t*)USART2_RxBuffer, USART2_RxBufferPtr); DBG_puts(" rcv\n");
			for(uint32_t i = 1; i < crcOfs; i++) USART2_RxBuffer[crcOfs] ^= USART2_RxBuffer[i];
			if(USART2_RxBuffer[crcOfs] != 0) {
				recvCmdCode = CMD_ST_WRONG_DATA;
			} else {
				recvCmdCode = USART2_RxBuffer[1];
				USART_ITConfig(USART1, USART_IT_RXNE, DISABLE);
			}
			USART2_RxBufferPtr = 0;
		}
	}
}

short CI_getLastCmdCode(void) {
	if(recvCmdCode == CMD_ST_NO_CMD) return CMD_ST_NO_CMD;
	if(recvCmdCode < 0) {
		short st = recvCmdCode;
		recvCmdCode = CMD_ST_NO_CMD;
		return st;
	}
	return recvCmdCode;
}

uint8_t CI_getCmdBody(uint8_t *data, int maxSz) {
	uint8_t sz = USART2_RxBuffer[2];
	if(data != NULL) {
		memcpy(data, (uint8_t*)USART2_RxBuffer+3, maxSz < sz? maxSz:sz);
		DBG_printf("getCmdBody [%d]:", sz); USART_DBG_hexDump((uint8_t*)USART2_RxBuffer+3, sz); DBG_puts("\n");
	}
	recvCmdCode = CMD_ST_NO_CMD;
	USART_ITConfig(USART1, USART_IT_RXNE, ENABLE);
	return sz;
}

//=========================================================================================
static volatile unsigned char USART1_TX_ring_buffer[USART_DBG_TX_BUFFER_SZ];
static volatile uint16_t USART_DBG_buffer_ptr_get = 0;
static volatile uint16_t USART_DBG_buffer_ptr_put = 0;
static volatile uint16_t USART_DBG_buffer_data_size = 0;

void USART1_IT_RxReady_exec(uint8_t c) {
	USART_DBG_putc(c); // echo
}

void USART1_IT_TxReady_exec() {
	if(USART_DBG_buffer_data_size > 0) {
		USART_SendData(USART1, (uint16_t)USART1_TX_ring_buffer[USART_DBG_buffer_ptr_get]);
		USART_DBG_buffer_ptr_get++; USART_DBG_buffer_data_size--;
		if(USART_DBG_buffer_ptr_get >= sizeof(USART1_TX_ring_buffer)) {
			USART_DBG_buffer_ptr_get = 0;
		}
	} else {
		USART_ITConfig(USART1, USART_IT_TXE, DISABLE);
	}
}

void USART_DBG_putc(char c) {
	USART_ITConfig(USART1, USART_IT_TXE, DISABLE);
 	USART1_TX_ring_buffer[USART_DBG_buffer_ptr_put] = c;
 	USART_DBG_buffer_ptr_put++;
 	USART_DBG_buffer_data_size++;
 	if(USART_DBG_buffer_ptr_put >= sizeof(USART1_TX_ring_buffer)) {
 		USART_DBG_buffer_ptr_put = 0;
 	}
 	USART_ITConfig(USART1, USART_IT_TXE, ENABLE);
}


void USART_DBG_puts(char *str) {
 for(int i = 0; str[i] != 0; i++) USART_DBG_putc(str[i]);
}

static unsigned char USART_DBG_itoa(long val, int radix, int len, char *sout, unsigned char ptr) {
	unsigned char c, r, sgn = 0, pad = ' ';
	unsigned char s[20], i = 0;
	unsigned long v;

	if (radix < 0) {
		radix = -radix;
		if (val < 0) {		val = -val;	sgn = '-';	}
	}
	v = val;
	r = radix;
	if (len < 0) {	len = -len;	pad = '0'; }
	if (len > 20) return ptr;
	do {
		c = (unsigned char)(v % r);
		if (c >= 10) c += 7;
		c += '0';
		s[i++] = c;
		v /= r;
	} while (v);
	if (sgn) s[i++] = sgn;
	while (i < len)	s[i++] = pad;
	do	sout[ptr++] = (s[--i]);
	while (i);
	return ptr;
}

void USART_DBG_bin(uint8_t *bin, uint16_t len) {
	USART_DBG_putc(0x1B);
	USART_DBG_putc((uint8_t)(len >> 8));
	USART_DBG_putc((uint8_t)len);
	for(int i = 0; i < len; i++) USART_DBG_putc(bin[i]);
}


void USART_DBG_hexDump(uint8_t *bin, uint8_t len) {
 char sout[512], *xStr = sout;
 static uint8_t xlat[16] =  { '0','1','2','3','4','5','6','7', '8','9','A','B','C','D','E','F'};
 *(xStr++) = '<';
 int   i;
 for (i=0; i<len; i++) {
  *(xStr++)=xlat[(*bin)>>4];
  *(xStr++)=xlat[(*bin)&0x0F];
  ++bin;
 }
 *(xStr++) = '>';
 *(xStr++) = 0;
 USART_DBG_puts(sout);
}


char *USART_DBG_printf(const char* str, ...) {
	va_list arp;
	int d, r, w, s, l;
	va_start(arp, str);
	static char sout[256];
	unsigned char ptr = 0;

	while ((d = *str++) != 0) {
			if (d != '%') {	sout[ptr++]=d; continue;	}
			d = *str++; w = r = s = l = 0;
			if (d == '0') {
				d = *str++; s = 1;
			}
			while ((d >= '0')&&(d <= '9')) {
				w += w * 10 + (d - '0');
				d = *str++;
			}
			if (s) w = -w;
			if (d == 'l') {
				l = 1;
				d = *str++;
			}
			if (!d) break;
			if (d == 's') {
				char *s = va_arg(arp, char*);
				while(*s != 0) { sout[ptr++] = *s; s++; }
				continue;
			}
			if (d == 'c') {
				sout[ptr++] = (char)va_arg(arp, int);
				continue;
			}
			if (d == 'u') r = 10;
			if (d == 'd') r = -10;
			if (d == 'X' || d == 'x') r = 16; // 'x' added by mthomas in increase compatibility
			if (d == 'b') r = 2;
			if (!r) break;
			if (l) {
				ptr = USART_DBG_itoa((long)va_arg(arp, long), r, w, sout, ptr);
			} else {
				if (r > 0) ptr = USART_DBG_itoa((unsigned long)va_arg(arp, int), r, w, sout, ptr);
				else	ptr = USART_DBG_itoa((long)va_arg(arp, int), r, w, sout, ptr);
			}
	}
	va_end(arp);
	sout[ptr] = 0;
	USART_DBG_puts(sout);
	return sout;
}



//=========================================================================================

void USART_init() {
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_USART1, ENABLE);
	RCC_APB1PeriphClockCmd(RCC_APB1Periph_USART2, ENABLE);

	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_50MHz;

	NVIC_PriorityGroupConfig(NVIC_PriorityGroup_1);
	NVIC_InitTypeDef NVIC_InitStructure;
	NVIC_InitStructure.NVIC_IRQChannel = USART1_IRQn;
	NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 2;
	NVIC_InitStructure.NVIC_IRQChannelSubPriority = 1;
	NVIC_InitStructure.NVIC_IRQChannelCmd = ENABLE;
	NVIC_Init(&NVIC_InitStructure);
	NVIC_InitStructure.NVIC_IRQChannel = USART2_IRQn;
	NVIC_InitStructure.NVIC_IRQChannelPreemptionPriority = 1;
	NVIC_Init(&NVIC_InitStructure);

	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_PP;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_9; // USAR1 TX
	GPIO_Init(GPIOA, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_2; // USAR2 TX
	GPIO_Init(GPIOA, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_IN_FLOATING;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_10; // USAR1 RX
	GPIO_Init(GPIOA, &GPIO_InitStructure);
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_3;  // USAR2 RX
	GPIO_Init(GPIOA, &GPIO_InitStructure);

	USART_InitTypeDef USART_InitStructure;
	USART_InitStructure.USART_BaudRate = 115200;
	USART_InitStructure.USART_WordLength = USART_WordLength_8b;
	USART_InitStructure.USART_StopBits = USART_StopBits_1;
	USART_InitStructure.USART_Parity = USART_Parity_No;
	USART_InitStructure.USART_HardwareFlowControl = USART_HardwareFlowControl_None;
	USART_InitStructure.USART_Mode = USART_Mode_Rx | USART_Mode_Tx;

	USART_Init(USART1, &USART_InitStructure);
	USART_Cmd(USART1, ENABLE);
	USART_ITConfig(USART1, USART_IT_RXNE, ENABLE);
	USART_ITConfig(USART1, USART_IT_TXE, DISABLE);

	USART_Init(USART2, &USART_InitStructure); USART_Cmd(USART2, ENABLE);
	USART_ITConfig(USART2, USART_IT_RXNE, ENABLE);
	USART_ITConfig(USART2, USART_IT_TXE, DISABLE);
}
// ---------------------------
