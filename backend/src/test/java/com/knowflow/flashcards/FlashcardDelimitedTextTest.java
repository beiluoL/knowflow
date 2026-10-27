package com.knowflow.flashcards;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class FlashcardDelimitedTextTest {
 @Test void quotedMultilineEscapesAndPhysicalRowNumbers(){
  var rows=FlashcardDelimitedText.parse("\ufefffront,back\r\n\"a,b\",\"x\r\ny\"\r\n\"say \"\"yes\"\"\",z", ',');
  assertEquals("a,b",rows.get(1).cells().get(0));assertEquals("x\r\ny",rows.get(1).cells().get(1));assertEquals(4,rows.get(2).line());assertEquals("say \"yes\"",rows.get(2).cells().get(0));
 }
 @Test void invalidQuotesBoundsAndDelimiter(){
  for(String s:new String[]{"front,back\na,b\"c", "front,back\n\"a\"x,b", "front,back\n\"unfinished"})assertThrows(IllegalArgumentException.class,()->FlashcardDelimitedText.parse(s,','));
  assertThrows(IllegalArgumentException.class,()->FlashcardDelimitedText.parse("a,".repeat(33),','));
  assertThrows(IllegalArgumentException.class,()->FlashcardDelimitedText.parse("a".repeat(50001),','));
  assertThrows(IllegalArgumentException.class,()->FlashcardDelimitedText.parse("a,b\n".repeat(1002),','));
  assertEquals("b",FlashcardDelimitedText.parse("front\tback\na\tb\n",'\t').get(1).cells().get(1));
 }
 @Test void exportRoundTripAndFormulaSafety(){
  String original="=SUM(1,2)\n\"quoted\"";
  String encoded=FlashcardDelimitedText.encode(java.util.List.of(java.util.List.of("front","back"),java.util.List.of(original,"answer")),',');
  assertEquals(original,FlashcardDelimitedText.parse(encoded,',').get(1).cells().get(0));
 }

 @Test void preservesQuotedCrLfAndBoundedExportEscapeOverhead(){
  String original="x\r\ny\rz";
  String encoded=FlashcardDelimitedText.encode(java.util.List.of(java.util.List.of("front","back"),java.util.List.of(original,"b")),',');
  assertEquals(original,FlashcardDelimitedText.parse(encoded,',').get(1).cells().get(0));
 }
 @Test void acceptsBoundedReversibleEscapeOverhead(){
  String escaped="'= "+"a".repeat(49998);
  assertEquals(50001,escaped.length());
  assertEquals(escaped,FlashcardDelimitedText.parse("front,back\n\""+escaped+"\",b",',').get(1).cells().get(0));
 }
}
