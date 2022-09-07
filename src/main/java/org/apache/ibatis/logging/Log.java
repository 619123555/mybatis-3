/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.logging;

/**
 * 统一日志接口(与slf4j框架统一各个不同的日志框架,提供一套统一的日志接口的性质一样).
 *
 * mybatis没有使用slf4j提供的统一日志规范,自己重新定义了一套接口.
 *
 * 非标准适配器模式.
 *  通过构造函数将被适配对象传进来,并通过统一的日志接口,调用被适配对象相应的方法.
 *
 * @author Clinton Begin
 */
public interface Log {

  boolean isDebugEnabled();

  boolean isTraceEnabled();

  void error(String s, Throwable e);

  void error(String s);

  void debug(String s);

  void trace(String s);

  void warn(String s);

}
