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
package org.apache.ibatis.builder;

import java.util.List;

import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;

/**
 * resultMap标签配置解析器.
 * 按指定的配置规则,创建ResultMap对象.
 * 使用当前对象的原因可能是因为当前ResultMap标签存在解析失败的情况,比如依赖的父ResultMap标签尚未解析成功,
 * 这种情况直接将当前对象放入队列中,放后续直接调用resolve()方法,再次尝试解析.
 *
 * @author Eduardo Macarron
 */
public class ResultMapResolver {
  private final MapperBuilderAssistant assistant;
  private final String id;
  private final Class<?> type;
  private final String extend;
  private final Discriminator discriminator;
  private final List<ResultMapping> resultMappings;
  private final Boolean autoMapping;

  public ResultMapResolver(MapperBuilderAssistant assistant, String id, Class<?> type, String extend, Discriminator discriminator, List<ResultMapping> resultMappings, Boolean autoMapping) {
    this.assistant = assistant;
    this.id = id;
    this.type = type;
    this.extend = extend;
    this.discriminator = discriminator;
    this.resultMappings = resultMappings;
    this.autoMapping = autoMapping;
  }

  public ResultMap resolve() {
    return assistant.addResultMap(this.id, this.type, this.extend, this.discriminator, this.resultMappings, this.autoMapping);
  }

}
