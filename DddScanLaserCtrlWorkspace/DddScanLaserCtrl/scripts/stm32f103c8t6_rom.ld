
/*ENTRY(Reset_Handler)*/
OUTPUT_FORMAT ("elf32-littlearm")

MEMORY
{
  flash   (rx)  : ORIGIN = 0x08000000, LENGTH = 64K
  ram    (rwx)  : ORIGIN = 0x20000000, LENGTH = 20K
}

/* define a global symbol _stack_end  */
_stack_end_ = ORIGIN(ram)+LENGTH(ram);


/* now define the output sections  */
SECTIONS 
{
	. = 0;								/* set location counter to address zero  */

	.text :								/* collect all sections that should go into FLASH after startup  */ 
	{
		. = ALIGN(4) ;
		KEEP(*(.flash_vec_table))
		*(.text)
		*(.text*)						/* all .text sections (code)  */
		*(.rodata)						/* all .rodata sections (constants, strings, etc.)  */
		*(.rodata*)						/* all .rodata* sections (constants, strings, etc.)  */
		*(.glue_7)						/* all .glue_7 sections  (no idea what these are) */
		*(.glue_7t)						/* all .glue_7t sections (no idea what these are) */
		*(.gnu*)
		*(.gcc*)
		. = ALIGN(4) ;
		_text_end_ = .;					/* define a global symbol _etext just after the last code byte */
	} >flash							/* put all the above into FLASH */

   
    _ram_vec_load_start_ = _text_end_ ;
	.ram_vec :								 
	{
		. = ALIGN(4) ;
		_ram_vec_start_ = .;				
		KEEP(*(.ram_vec))
		_ram_vec_end_ = ALIGN( . , 4) ;					
	}  >ram	AT> flash
	 _ram_vec_load_end_ = _ram_vec_load_start_ + SIZEOF(.ram_vec) ;				

   _data_load_start_ = _ram_vec_load_end_ ;
   .data :								/* collect all initialized .data sections that go into RAM  */ 
	{
		. = ALIGN(4) ;
		_data_start_ = .;						/* create a global symbol marking the start of the .data section  */
		*(.data)						/* all .data sections  */
		*(.data*)
		_data_end_ = ALIGN( . , 4);							/* define a global symbol marking the end of the .data section  */
	} >ram AT >flash					/* put all the above into RAM (but load the LMA copy into FLASH) */
  _data_load_end_ = _data_load_start_ +  SIZEOF(.data) ;	
   
   _fast_load_start_ = _data_load_end_ ;
   .fast :
   {
     . = ALIGN(4) ;
     _fast_start_ = .;
     *(.fast)
     *(.fast*)
     _fast_end_ = ALIGN( . , 4);
   } >ram AT >flash
   _fast_load_end_ = _fast_load_start_ +  SIZEOF(.fast) ;
   _flash_end_ = _fast_load_end_ ;
	
	
	.bss :	
	{
		. = ALIGN(4) ;
		_bss_start_ = .;
		*(.bss)					
	    *(.bss*)
	    _bss_end_ = .;
	} >ram	

   DISCARD :
    {
     libc.a ( * )
     libm.a ( * )
     libgcc.a ( * )
     }	
	
	  
}


