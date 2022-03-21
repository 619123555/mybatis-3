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

import com.personal.bean.Emp;
import com.personal.dao.EmpDao;
import java.io.IOException;
import java.io.InputStream;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.Before;
import org.junit.Test;

public class MyTest {

    // 全局会话工厂,负责创建SqlSession对象.
    SqlSessionFactory sqlSessionFactory = null;

    @Before
    public void init(){
      // 根据全局配置文件创建出SqlSessionFactory
      // SqlSessionFactory:负责创建SqlSession对象的工厂
      // SqlSession:表示跟数据库建议的一次会话
      String resource = "mybatis-config.xml";
      // 获取配置文件输入字节流对象.
      InputStream inputStream = null;
      try {
        inputStream = Resources.getResourceAsStream(resource);
      } catch (IOException e) {
        e.printStackTrace();
      }
      // 创建全局SqlSessionFactory对象(全局会话工厂,负责创建SqlSession对象).
      sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    }

    @Test
    public void test01() {

        // 获取数据库的会话,创建出数据库连接的会话对象（事务工厂，事务对象，执行器，如果有插件的话会进行插件的解析）
        SqlSession sqlSession = sqlSessionFactory.openSession();
        Emp empByEmpno = null;
        try {
            // 获取要调用的接口类,创建出对应的mapper的动态代理对象（mapperRegistry.knownMapper）
            EmpDao mapper = sqlSession.getMapper(EmpDao.class);
            // 调用方法开始执行
            empByEmpno = mapper.findEmpByEmpno(7369);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            sqlSession.close();
        }
        System.out.println(empByEmpno);
    }

  @Test
  public void test02() {
    // 根据全局配置文件创建出SqlSessionFactory
    // SqlSessionFactory:负责创建SqlSession对象的工厂
    // SqlSession:表示跟数据库建议的一次会话
    String resource = "mybatis-config.xml";
    InputStream inputStream = null;
    try {
      inputStream = Resources.getResourceAsStream(resource);
    } catch (IOException e) {
      e.printStackTrace();
    }
    SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);
    // 获取数据库的会话,创建出数据库连接的会话对象（事务工厂，事务对象，执行器，如果有插件的话会进行插件的解析）
    SqlSession sqlSession = sqlSessionFactory.openSession();
    Emp empByEmpno = null;
    try {
      // 获取要调用的接口类,创建出对应的mapper的动态代理对象（mapperRegistry.knownMapper）
      EmpDao mapper = sqlSession.getMapper(EmpDao.class);
      // 调用方法开始执行
      empByEmpno = mapper.findEmpByEmpnoAndEname(7369,"SMITH");
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      sqlSession.close();
    }
    System.out.println(empByEmpno);
  }

  @Test
  public void test03(){
    SqlSession sqlSession = sqlSessionFactory.openSession();
    EmpDao mapper = sqlSession.getMapper(EmpDao.class);
    int zhangsan = mapper.insert(new Emp(1111, "zhangsan"));
    System.out.println(zhangsan);
    sqlSession.commit();
    sqlSession.close();
  }

  @Test
  public void test04(){
    SqlSession sqlSession = sqlSessionFactory.openSession();
    EmpDao mapper = sqlSession.getMapper(EmpDao.class);
    int zhangsan = mapper.update(new Emp(1111, "lisi"));
    System.out.println(zhangsan);
    sqlSession.commit();
    sqlSession.close();
  }


  @Test
  public void test05(){
    SqlSession sqlSession = sqlSessionFactory.openSession();
    EmpDao mapper = sqlSession.getMapper(EmpDao.class);
    int zhangsan = mapper.delete(1111);
    System.out.println(zhangsan);
    sqlSession.commit();
    sqlSession.close();
  }

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
}
