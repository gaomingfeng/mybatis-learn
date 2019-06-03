/**
 *    Copyright 2009-2017 the original author or authors.
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
package org.apache.ibatis.reflection.wrapper;

import java.util.List;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ReflectionException;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * 普通java对象包装类
 * @author Clinton Begin
 */
public class BeanWrapper extends BaseWrapper {
  /**被包装的java对象*/
  private final Object object;
  /**该java对象的所对应类型数据*/
  private final MetaClass metaClass;

  public BeanWrapper(MetaObject metaObject, Object object) {
    super(metaObject);
    this.object = object;
    this.metaClass = MetaClass.forClass(object.getClass(), metaObject.getReflectorFactory());
  }

  /**
   * 根据属性表达式， 获取属性值
   * @param prop 属性表达式
   * @return
   */
  @Override
  public Object get(PropertyTokenizer prop) {
    //判断当前属性是否是集合类型
    if (prop.getIndex() != null) {
      Object collection = resolveCollection(prop, object);
      return getCollectionValue(prop, collection);
    } else {
      //获取普通java对象属性值
      return getBeanProperty(prop, object);
    }
  }

  /**
   * 设置属性值
   * @param prop
   * @param value
   */
  @Override
  public void set(PropertyTokenizer prop, Object value) {
    if (prop.getIndex() != null) {
      Object collection = resolveCollection(prop, object);
      setCollectionValue(prop, collection, value);
    } else {
      setBeanProperty(prop, object, value);
    }
  }

  /**
   * 根据属性表达式, 查询属性
   * @param name 属性表达式
   * @param useCamelCaseMapping 是否使用驼峰风格
   * @return
   */
  @Override
  public String findProperty(String name, boolean useCamelCaseMapping) {
    return metaClass.findProperty(name, useCamelCaseMapping);
  }

  /**
   * 获取读属性集合
   * @return
   */
  @Override
  public String[] getGetterNames() {
    return metaClass.getGetterNames();
  }

  /**
   * 获取写属性集合
   * @return
   */
  @Override
  public String[] getSetterNames() {
    return metaClass.getSetterNames();
  }

  /**
   * 根据属性表达式，获取对应的set方法参数类型
   * @param name  属性表达式 例如：user.order[0].product.name
   * @return
   */
  @Override
  public Class<?> getSetterType(String name) {
    //属性解析
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //判断是否存在子属性
    if (prop.hasNext()) {
      //获取当前属性的属性值, 包装成MetaObject返回
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      //判断返回的属性值是否为null
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        //使用metaClass, 递归获取
        return metaClass.getSetterType(name);
      } else {
        //返回的属性值MetaObject, 递归获取
        return metaValue.getSetterType(prop.getChildren());
      }
    } else {
      //出口
      return metaClass.getSetterType(name);
    }
  }

  /**
   * 根据属性表达式，获取对应的get方法返回类型
   * @param name  属性表达式 例如：user.order[0].product.name
   * @return
   */
  @Override
  public Class<?> getGetterType(String name) {
    //属性解析
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //判断是否存在子属性
    if (prop.hasNext()) {
      //获取当前属性的属性值, 包装成MetaObject返回
      MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
      //判断返回的属性值是否为null
      if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
        //使用metaClass, 递归获取
        return metaClass.getGetterType(name);
      } else {
        //返回的属性值MetaObject, 递归获取
        return metaValue.getGetterType(prop.getChildren());
      }
    } else {
      //出口
      return metaClass.getGetterType(name);
    }
  }

  /**
   * 判断是否存在属性表达式的set 方法
   * @param name  属性表达式 例如：user.order[0].product.name
   * @return
   */
  @Override
  public boolean hasSetter(String name) {
    //属性解析
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //判断是否存在子属性
    if (prop.hasNext()) {
      //判断当前属性是否存在set 方法
      if (metaClass.hasSetter(prop.getIndexedName())) {
        //获取当前属性的属性值, 包装成MetaObject返回
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        //判断返回的属性值是否为null, 如果属性值为null, 则使用metaClass来查找判断
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasSetter(name);
        } else {
          //递归判断
          return metaValue.hasSetter(prop.getChildren());
        }
      } else {
        //不存在直接返回false
        return false;
      }
    } else {
      //出口
      return metaClass.hasSetter(name);
    }
  }

  /**
   * 判断是否存在属性表达式的get 方法
   * @param name  属性表达式 例如：user.order[0].product.name
   * @return
   */
  @Override
  public boolean hasGetter(String name) {
    //属性解析
    PropertyTokenizer prop = new PropertyTokenizer(name);
    //判断是否存在子属性
    if (prop.hasNext()) {
      //判断当前属性是否存在get 方法
      if (metaClass.hasGetter(prop.getIndexedName())) {
        //获取当前属性的属性值, 包装成MetaObject返回
        MetaObject metaValue = metaObject.metaObjectForProperty(prop.getIndexedName());
        //判断返回的属性值是否为null, 如果属性值为null, 则使用metaClass来查找判断
        if (metaValue == SystemMetaObject.NULL_META_OBJECT) {
          return metaClass.hasGetter(name);
        } else {
          //递归判断
          return metaValue.hasGetter(prop.getChildren());
        }
      } else {
        //不存在直接返回false
        return false;
      }
    } else {
      //出口
      return metaClass.hasGetter(name);
    }
  }

  /**
   * 初始化属性值
   * @param name  属性表达式
   * @param prop  属性解析器
   * @param objectFactory 对象工厂
   * @return
   */
  @Override
  public MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory) {
    MetaObject metaValue;
    Class<?> type = getSetterType(prop.getName());
    try {
      Object newObject = objectFactory.create(type);
      metaValue = MetaObject.forObject(newObject, metaObject.getObjectFactory(), metaObject.getObjectWrapperFactory(), metaObject.getReflectorFactory());
      set(prop, newObject);
    } catch (Exception e) {
      throw new ReflectionException("Cannot set value of property '" + name + "' because '" + name + "' is null and cannot be instantiated on instance of " + type.getName() + ". Cause:" + e.toString(), e);
    }
    return metaValue;
  }

  /**
   *获取对应属性值
   * @param prop  属性解析器
   * @param object  所属对象
   * @return
   */
  private Object getBeanProperty(PropertyTokenizer prop, Object object) {
    try {
      //获取属性的get方法Invoker对象
      Invoker method = metaClass.getGetInvoker(prop.getName());
      try {
        return method.invoke(object, NO_ARGUMENTS);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable t) {
      throw new ReflectionException("Could not get property '" + prop.getName() + "' from " + object.getClass() + ".  Cause: " + t.toString(), t);
    }
  }

  /**
   * 设置属性值
   * @param prop
   * @param object
   * @param value
   */
  private void setBeanProperty(PropertyTokenizer prop, Object object, Object value) {
    try {
      Invoker method = metaClass.getSetInvoker(prop.getName());
      Object[] params = {value};
      try {
        method.invoke(object, params);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    } catch (Throwable t) {
      throw new ReflectionException("Could not set property '" + prop.getName() + "' of '" + object.getClass() + "' with value '" + value + "' Cause: " + t.toString(), t);
    }
  }

  @Override
  public boolean isCollection() {
    return false;
  }

  @Override
  public void add(Object element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <E> void addAll(List<E> list) {
    throw new UnsupportedOperationException();
  }

}
