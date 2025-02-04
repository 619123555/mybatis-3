/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拦截器链.
 *  借助动态代理实现的职责链.
 *  拦截在执行sql过程中的某些方法调用,在拦截的方法调用前后,执行一些额外的代码逻辑.
 *
 *
 * 职责链模式.
 *
 * @author Clinton Begin
 */
public class InterceptorChain {

  // 存储所有拦截器对象的集合.
  private final List<Interceptor> interceptors = new ArrayList<>();

  public Object pluginAll(Object target) {
    // 遍历所有拦截器,并调用每个拦截器的plugin方法.
    for (Interceptor interceptor : interceptors) {
      target = interceptor.plugin(target);
    }
    return target;
  }

  public void addInterceptor(Interceptor interceptor) {
    interceptors.add(interceptor);
  }

  public List<Interceptor> getInterceptors() {
    return Collections.unmodifiableList(interceptors);
  }

}
