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
package org.apache.ibatis.parsing;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.ibatis.builder.BuilderException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * XPath解析器(XPath是一门在XML文档中查找信息的语言),用的都是JDK的类包,封装了一下,使用起来更方便.
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XPathParser {

  // 文档对象.
  private final Document document;
  // 是否开启验证.
  private boolean validation;
  // 用于加载本地DTD文件.
  private EntityResolver entityResolver;
  // 1.mybatis-config.xml中Configuration -> properties标签定义的键值对.
  // 2.properties文件中定义的键值对.
  private Properties variables;
  // Xpath对象.
  private XPath xpath;

  // 一些构造函数,全部调用commonConstructor以及createDocument.
  // 1-4方法,默认不需要验证.
  public XPathParser(String xml) {
    commonConstructor(false, null, null);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader) {
    commonConstructor(false, null, null);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream) {
    commonConstructor(false, null, null);
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document) {
    commonConstructor(false, null, null);
    this.document = document;
  }

  // 5-8,传入是否需要验证参数.
  public XPathParser(String xml, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document, boolean validation) {
    commonConstructor(validation, null, null);
    this.document = document;
  }

  // 9-12,传入是否需要验证参数,Properties.
  public XPathParser(String xml, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document, boolean validation, Properties variables) {
    commonConstructor(validation, variables, null);
    this.document = document;
  }

  // 13-16,传入是否需要验证参数,Properties,EntityResolver.
  public XPathParser(String xml, boolean validation, Properties variables, EntityResolver entityResolver) {
    commonConstructor(validation, variables, entityResolver);
    this.document = createDocument(new InputSource(new StringReader(xml)));
  }

  public XPathParser(Reader reader, boolean validation, Properties variables, EntityResolver entityResolver) {
    commonConstructor(validation, variables, entityResolver);
    this.document = createDocument(new InputSource(reader));
  }

  public XPathParser(InputStream inputStream, boolean validation, Properties variables, EntityResolver entityResolver) {
    // 将参数赋值给当前对象的各个属性中,同时创建XPath对象.
    commonConstructor(validation, variables, entityResolver);
    // 通过jdk中的XPath工具,将xml文件的字节流对象转换为document对象.
    this.document = createDocument(new InputSource(inputStream));
  }

  public XPathParser(Document document, boolean validation, Properties variables, EntityResolver entityResolver) {
    commonConstructor(validation, variables, entityResolver);
    this.document = document;
  }

  // 设置Properties类型的variables对象.
  public void setVariables(Properties variables) {
    this.variables = variables;
  }

  // 通过一系列的eval*方法来解析boolean,short,long,int,string,Node等类型的信息.
  public String evalString(String expression) {
    return evalString(document, expression);
  }

  public String evalString(Object root, String expression) {
    // 1.先用xpath解析.
    String result = (String) evaluate(expression, root, XPathConstants.STRING);
    // 2.再调用PropertyParser去解析,也就是替换 ${} 这种格式的字符串.
    result = PropertyParser.parse(result, variables);
    return result;
  }

  public Boolean evalBoolean(String expression) {
    return evalBoolean(document, expression);
  }

  public Boolean evalBoolean(Object root, String expression) {
    return (Boolean) evaluate(expression, root, XPathConstants.BOOLEAN);
  }

  public Short evalShort(String expression) {
    return evalShort(document, expression);
  }

  public Short evalShort(Object root, String expression) {
    return Short.valueOf(evalString(root, expression));
  }

  public Integer evalInteger(String expression) {
    return evalInteger(document, expression);
  }

  public Integer evalInteger(Object root, String expression) {
    return Integer.valueOf(evalString(root, expression));
  }

  public Long evalLong(String expression) {
    return evalLong(document, expression);
  }

  public Long evalLong(Object root, String expression) {
    return Long.valueOf(evalString(root, expression));
  }

  public Float evalFloat(String expression) {
    return evalFloat(document, expression);
  }

  public Float evalFloat(Object root, String expression) {
    return Float.valueOf(evalString(root, expression));
  }

  public Double evalDouble(String expression) {
    return evalDouble(document, expression);
  }

  public Double evalDouble(Object root, String expression) {
    return (Double) evaluate(expression, root, XPathConstants.NUMBER);
  }

  public List<XNode> evalNodes(String expression) {
    return evalNodes(document, expression);
  }

  // 返回节点List.
  public List<XNode> evalNodes(Object root, String expression) {
    List<XNode> xnodes = new ArrayList<>();
    NodeList nodes = (NodeList) evaluate(expression, root, XPathConstants.NODESET);
    for (int i = 0; i < nodes.getLength(); i++) {
      xnodes.add(new XNode(this, nodes.item(i), variables));
    }
    return xnodes;
  }

  public XNode evalNode(String expression) {
    return evalNode(document, expression);
  }

  // 获取指定节点的XNode对象.
  // 并尝试将该节点中的${}占位符替换为在Configuration -> variables集合中指定属性的value,获取不到则保持原样.
  public XNode evalNode(Object root, String expression) {
    Node node = (Node) evaluate(expression, root, XPathConstants.NODE);
    if (node == null) {
      return null;
    }
    return new XNode(this, node, variables);
  }

  private Object evaluate(String expression, Object root, QName returnType) {
    try {
      // 最终都会走到这,直接调用XPath.evaluate()方法.
      return xpath.evaluate(expression, root, returnType);
    } catch (Exception e) {
      throw new BuilderException("Error evaluating XPath.  Cause: " + e, e);
    }
  }

  private Document createDocument(InputSource inputSource) {
    // important: this must only be called AFTER common constructor
    // 在创建文档之前一定要先调用commonConstructor方法完成当前对象的初始化操作.
    try {
      // 创建DocumentBuilderFactory对象.
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      // 对DocumentBuilderFactory对象进行一系列的属性配置.
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      // 设置是否需要校验xml文件的合法性.
      factory.setValidating(validation);
      // 命名空间.
      factory.setNamespaceAware(false);
      // 是否忽略注释.
      factory.setIgnoringComments(true);
      // 是否忽略空白.
      factory.setIgnoringElementContentWhitespace(false);
      // 是否将 CDATA 节点转换为 Text 节点.
      factory.setCoalescing(false);
      // 扩展实体引用.
      factory.setExpandEntityReferences(true);

      // 创建DocumentBuilder对象并进行配置.
      DocumentBuilder builder = factory.newDocumentBuilder();
      // 定义了EntityResolver(XMLMapperEntityResolver)后,可以不用联网去获取DTD文件,直接加载本地DTD文件来验证xml合法性.
      // org\apache\ibatis\builder\xml\mybatis-3-config.dtd.
      builder.setEntityResolver(entityResolver);
      // 设置ErrorHandler对象,方法都是空实现.
      builder.setErrorHandler(new ErrorHandler() {
        @Override
        public void error(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
          throw exception;
        }

        @Override
        public void warning(SAXParseException exception) throws SAXException {
          // NOP
        }
      });
      // 加载xml文件.
      return builder.parse(inputSource);
    } catch (Exception e) {
      throw new BuilderException("Error creating document instance.  Cause: " + e, e);
    }
  }

  private void commonConstructor(boolean validation, Properties variables, EntityResolver entityResolver) {
    // 设置是否对xml文件内容进行合法性校验.
    this.validation = validation;
    // 设置xml实体映射解析器,通过dtd文件来验证xml合法性.
    this.entityResolver = entityResolver;
    this.variables = variables;
    // 获取jdk中的XPathFactory对象(用来创建具体XPath对象).
    XPathFactory factory = XPathFactory.newInstance();
    // 由XPathFactory工厂对象创建Xpath对象.
    this.xpath = factory.newXPath();
  }

}
