/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.parsing;

/**
 * 通用标记解析器,将使用了#{},${}或其他标记,替换为指定的字符串.
 *
 * @author Clinton Begin
 */
public class GenericTokenParser {

  // 占位符的开始字符串格式.
  private final String openToken;
  // 占位符的结束字符串格式.
  private final String closeToken;
  // TokenHandler接口的实现会按照一定的逻辑将解析的占位符替换为指定的字符串.
  private final TokenHandler handler;

  public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
    this.openToken = openToken;
    this.closeToken = closeToken;
    this.handler = handler;
  }

  public String parse(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    // search open token
    // 搜索 开始字符串格式 的下标.
    int start = text.indexOf(openToken);
    if (start == -1) {
      // 没找到则直接返回.
      return text;
    }
    char[] src = text.toCharArray();
    int offset = 0;
    final StringBuilder builder = new StringBuilder();
    // 临时存储每个${}或#{}占位符中的参数名称.
    StringBuilder expression = null;
    do {
      // 如果 开始字符串格式 不为空,并且前边是\\符号,则将\\符号删除并替换为 开始字符串格式.
      if (start > 0 && src[start - 1] == '\\') {
        // this open token is escaped. remove the backslash and continue.
        builder.append(src, offset, start - offset - 1).append(openToken);
        offset = start + openToken.length();
      } else {
        // found open token. let's search close token.
        if (expression == null) {
          expression = new StringBuilder();
        } else {
          expression.setLength(0);
        }
        // 截取 开始字符串格式 之前的字符串.
        builder.append(src, offset, start - offset);
        // 设置下次循环时的开始下标.
        offset = start + openToken.length();
        // 获取 开始字符串格式 后的 关闭字符串格式 下标.
        int end = text.indexOf(closeToken, offset);
        while (end > -1) {
          if (end > offset && src[end - 1] == '\\') {
            // this close token is escaped. remove the backslash and continue.
            expression.append(src, offset, end - offset - 1).append(closeToken);
            offset = end + closeToken.length();
            end = text.indexOf(closeToken, offset);
          } else {
            expression.append(src, offset, end - offset);
            break;
          }
        }
        if (end == -1) {
          // close token was not found.
          builder.append(src, start, src.length - start);
          offset = src.length;
        } else {
          // 设置当前TextSqlNode为isDynamic动态sql,并修改原sql的占位符为 null 值字符串.
          builder.append(handler.handleToken(expression.toString()));
          offset = end + closeToken.length();
        }
      }
      start = text.indexOf(openToken, offset);
    } while (start > -1);
    if (offset < src.length) {
      builder.append(src, offset, src.length - offset);
    }
    return builder.toString();
  }
}
