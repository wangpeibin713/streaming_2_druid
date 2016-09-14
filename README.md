# streaming_2_druid-基于spark-streaming框架的druid实时导入任务。


##基本思路
---
*  通过spark-streaming 订阅kafka中的数据，通过对应的解析器抽取出特定的字段，得到对应的dstream
*  将每个dstream 的RDD转换成DataFrame注册成临时表，使用Spark SQL结合业务逻辑对临时表进行业务处理后到达处理后的DataFame
*  将得到的DataFrame通过druid的tranquility接口实时导入到druid中

