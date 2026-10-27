package com.knowflow.flashcards;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowflow.exception.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
class FlashcardExchangeExportLimitTest {
 @Test void exportRejectsExpandedCardCountThatCannotBeReimported(){
  var jdbc=mock(JdbcTemplate.class);var rows=new ArrayList<Map<String,Object>>();StringBuilder front=new StringBuilder();
  for(int ordinal=1;ordinal<=100;ordinal++)front.append("{{c").append(ordinal).append("::answer}} ");
  for(long id=1;id<=21;id++)rows.add(Map.of("id",id,"note_type","CLOZE","front",front.toString(),"back","","language","java"));
  when(jdbc.queryForList(anyString(),eq(123L))).thenReturn(rows,List.of());
  var service=new FlashcardExchangeService(jdbc,mock(FlashcardNoteService.class),new ObjectMapper());
  var error=assertThrows(BusinessException.class,()->service.export(123L,"csv"));assertTrue(error.getMessage().contains("2000"));
 }
}
