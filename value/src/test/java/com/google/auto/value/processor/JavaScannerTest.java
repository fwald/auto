/*
 * Copyright 2015 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author emcmanus@google.com (Éamonn McManus) */
@RunWith(JUnit4.class)
public class JavaScannerTest {
  private static final ImmutableList<String> TOKENS =
      ImmutableList.of(
          "  ",
          "\"hello \\\" world\\n\"",
          "'a'",
          "  ",
          "'\\t'",
          "  ",
          "`com.google.Foo`",
          "   ",
          "\n  ",
          "/* comment * comment \" whatever\n     comment continued */",
          "  ",
          "t",
          "h",
          "i",
          "n",
          "g",
          " ",
          "t",
          "h",
          "i",
          "n",
          "g",
          "  ",
          "// line comment",
          "\n",
          "/*/ tricky comment */",
          "\n");

  /**
   * Tests basic scanner functionality. The test concatenates the tokens in {@link #TOKENS} and then
   * retokenizes that string, checking that the same list of tokens is produced.
   */
  @Test
  public void testScanner() {
    String input = Joiner.on("").join(TOKENS);
    ImmutableList.Builder<String> tokensBuilder = ImmutableList.builder();
    JavaScanner tokenizer = new JavaScanner(input);
    int end;
    for (int i = 0; i < input.length(); i = end) {
      end = tokenizer.tokenEnd(i);
      tokensBuilder.add(input.substring(i, end));
    }
    assertThat(tokensBuilder.build()).containsExactlyElementsIn(TOKENS).inOrder();
  }

  /**
  * Tests that tokenEnd in scanner returns the length of the string l when start s >= l.
  */
  @Test
  public void testScannerTokenEndStartOverflow(){
    JavaScanner tokenizer = new JavaScanner("Google\n");
    assertThat(tokenizer.tokenEnd(7)).isEqualTo(7);
    assertThat(tokenizer.tokenEnd(8)).isEqualTo(7);
  }

  /**
   * Tests that tokenEnd in scanner works with tokens that start with / but are not comments.
   */
  @Test
  public void testScannerTokenEndHalfComment(){
    JavaScanner tokenizer = new JavaScanner("1/2\n");
    assertThat(tokenizer.tokenEnd(1)).isEqualTo(2);
  }
}
