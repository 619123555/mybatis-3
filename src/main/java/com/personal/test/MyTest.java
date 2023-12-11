/**
 *    Copyright 2009-2022 the original author or authors.
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
package com.personal.test;

import com.github.pagehelper.Page;
import com.personal.bean.Emp;
import com.personal.bean.SubEmp;
import com.personal.dao.EmpDao;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.sf.cglib.core.DebuggingClassWriter;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Before;
import org.junit.Test;

public class MyTest {

    // 全局会话工厂,负责创建SqlSession对象.
    SqlSessionFactory sqlSessionFactory = null;

    @Before
    public void init() throws Exception {
      saveGeneratedCGlibProxyFiles(System.getProperty("user.dir") + "/proxy");
      // 根据全局配置文件创建出SqlSessionFactory
      // SqlSessionFactory: 负责创建SqlSession对象的工厂.
      // SqlSession: 跟数据库建立的会话对象.
      String resource = "mybatis-config.xml";
      // 获取配置文件输入字节流对象.
      InputStream inputStream = null;
      try {
        inputStream = Resources.getResourceAsStream(resource);
      } catch (IOException e) {
        e.printStackTrace();
      }
      // 创建持有Configuration对象的DefaultSqlSessionFactory对象(全局会话工厂,负责创建SqlSession对象).
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    }

//    @Test
//    public void constructResultMap() {
//      // 获取数据库的会话对象,它持有数据库连接对象,事务工厂,事务对象,执行器(调用插件的plugin()方法,可能会返回一个执行器对象).
//      // 这里只是创建对象,但没有与数据库进行连接.
//      SqlSession sqlSession = sqlSessionFactory.openSession();
//      SubEmp empByEmpno = null;
//      try {
//        // 通过要调用的接口类,去knowMapper集合中获取对应的MapperProxyFactory对象,并通过该对象来创建动态代理对象(mapperRegistry.knownMapper).
//        EmpDao mapper = sqlSession.getMapper(EmpDao.class);
//        // 调用代理方法开始执行.
//        empByEmpno = mapper.constructResultMapTest();
//      } catch (Exception e) {
//        e.printStackTrace();
//      } finally {
//        sqlSession.close();
//      }
//      System.out.println(empByEmpno);
//    }

  @Test
  public void associationResultMapTest() throws Exception {
    saveGeneratedCGlibProxyFiles(System.getProperty("user.dir") + "/proxy");
    // 获取数据库的会话对象,它持有数据库连接对象,事务工厂,事务对象,执行器(调用插件的plugin()方法,可能会返回一个执行器对象).
    SqlSession sqlSession = sqlSessionFactory.openSession();
    List<Map<String, Object>> empByEmpno = null;
    try {
      // 通过要调用的接口类,去knowMapper集合中获取对应的MapperProxyFactory对象,并通过该对象来创建动态代理对象(mapperRegistry.knownMapper).
      EmpDao mapper = sqlSession.getMapper(EmpDao.class);
//      RowBounds rb = new RowBounds(10, 5);
      String orderBy = " order by ename limit 10,5";
      Page<Emp> page = new Page<Emp>(1, 5, true);
      // 调用代理方法开始执行.
      empByEmpno = mapper.associationResultMapTest(orderBy);
      System.out.println(empByEmpno);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      sqlSession.close();
    }
    System.out.println(empByEmpno);
  }

//    @Test
//    public void customTypeHandlerTest() {
//        // 获取数据库的会话对象,它持有数据库连接对象,事务工厂,事务对象,执行器(调用插件的plugin()方法,可能会返回一个执行器对象).
//        SqlSession sqlSession = sqlSessionFactory.openSession();
//        Emp empByEmpno = null;
//        try {
//            // 通过要调用的接口类,去knowMapper集合中获取对应的MapperProxyFactory对象,并通过该对象来创建动态代理对象(mapperRegistry.knownMapper).
//            EmpDao mapper = sqlSession.getMapper(EmpDao.class);
//            // 调用代理方法开始执行.
////            empByEmpno = mapper.customTypeHandlerTest();
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            sqlSession.close();
//        }
//        System.out.println(empByEmpno);
//    }
//
//  @Test
//  public void test02() {
//    // 根据全局配置文件创建出SqlSessionFactory
//    // SqlSessionFactory:负责创建SqlSession对象的工厂
//    // SqlSession:表示跟数据库建议的一次会话
//    String resource = "mybatis-config.xml";
//    InputStream inputStream = null;
//    try {
//      inputStream = Resources.getResourceAsStream(resource);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
//    // 获取数据库的会话,创建出数据库连接的会话对象（事务工厂，事务对象，执行器，如果有插件的话会进行插件的解析）
//    SqlSession sqlSession = sqlSessionFactory.openSession();
//    Emp empByEmpno = null;
//    try {
//      // 获取要调用的接口类,创建出对应的mapper的动态代理对象（mapperRegistry.knownMapper）
//      EmpDao mapper = sqlSession.getMapper(EmpDao.class);
//      // 调用方法开始执行
////      empByEmpno = mapper.findEmpByEmpnoAndEname(7369,"SMITH");
//    } catch (Exception e) {
//      e.printStackTrace();
//    } finally {
//      sqlSession.close();
//    }
//    System.out.println(empByEmpno);
//  }
//
//  @Test
//  public void test03(){
//    SqlSession sqlSession = sqlSessionFactory.openSession();
//    EmpDao mapper = sqlSession.getMapper(EmpDao.class);
////    int zhangsan = mapper.insert(new Emp(1111, "zhangsan"));
////    System.out.println(zhangsan);
//    sqlSession.commit();
//    sqlSession.close();
//  }
//
//  @Test
//  public void test04(){
//    SqlSession sqlSession = sqlSessionFactory.openSession();
//    EmpDao mapper = sqlSession.getMapper(EmpDao.class);
////    int zhangsan = mapper.update(new Emp(1111, "lisi"));
////    System.out.println(zhangsan);
//    sqlSession.commit();
//    sqlSession.close();
//  }
//
//
//  @Test
//  public void test05(){
//    SqlSession sqlSession = sqlSessionFactory.openSession();
//    EmpDao mapper = sqlSession.getMapper(EmpDao.class);
//    RowBounds rb = new RowBounds(1, 10);
//    List<String> list = new ArrayList<>();
//    list.add("23432");
////    List<Emp> zhangsan = mapper.selectByStartingWithName(rb, list);
////    System.out.println(zhangsan);
//    sqlSession.commit();
//    sqlSession.close();
//  }

//  @Test
//  public void testPageHelper(){
//    // 设置分页参数
//    PageHelper.startPage(1,2);
//    String resource = "mybatis-config.xml";
//    InputStream inputStream = null;
//    try {
//      inputStream = Resources.getResourceAsStream(resource);
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
//    // 获取数据库的会话
//    SqlSession sqlSession = sqlSessionFactory.openSession();
//    EmpDao mapper = sqlSession.getMapper(EmpDao.class);
//    List<Emp> empList = mapper.selectAll();
//    for (Emp emp : empList) {
//      System.out.println(emp);
//    }
//
//    PageInfo<Emp> pageInfo = new PageInfo<>(empList);
//    System.out.println("总条数"+pageInfo.getTotal());
//    System.out.println("总页数"+pageInfo.getPages());
//    System.out.println("当前页"+pageInfo.getPageNum());
//    System.out.println("每页显示长度"+pageInfo.getPageSize());
//    System.out.println("是否第一页"+pageInfo.isIsFirstPage());
//    System.out.println("是否最后一页"+pageInfo.isIsLastPage());
//  }

  public static void saveGeneratedCGlibProxyFiles(String dir) throws Exception {
    Field field = System.class.getDeclaredField("props");
    field.setAccessible(true);
    Properties props = (Properties) field.get(null);
    System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, dir);//dir为保存文件路径
    props.put("net.sf.cglib.core.DebuggingClassWriter.traceEnabled", "true");
  }
}
