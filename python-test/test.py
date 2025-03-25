import time
import os

from py4j.java_gateway import JavaGateway

gateway = JavaGateway()                   # connect to the JVM
random = gateway.jvm.java.util.Random()   # create a java.util.Random instance
number1 = random.nextInt(10)              # call the Random.nextInt method
number2 = random.nextInt(10)
print(number1, number2)
addition_app = gateway.entry_point               # get the AdditionApplication instance
value = addition_app.addition(number1, number2) # call the addition method
print(value)

# spark = SparkSession.builder\
#     .appName("Scala Function Example")\
#     .master("local[*]")\
#     .config(map={"spark.jars": os.path.join(os.path.dirname(__file__), "../target/scala-2.12/scala-isabelle-assembly-1.0.jar"),
#                         "spark.network.maxRemoteBlockSizeFetchToMem": "2147483135", 
#                         "spark.driver.memory": "8g", 
#                         "spark.executor.memory": "8g",
#                         "spark.network.timeout": "120000s", 
#                         "spark.sql.shuffle.partitions": '4096',
#                         "spark.default.parallelism": "4096",
#                         "spark.shuffle.file.buffer": "2048k",
#                         # "spark.driver.userClassPathFirst": True, 
#                         # "spark.executor.userClassPathFirst": True,
#                         "spark.ui.port": 4050})\
#     .getOrCreate()  

# spark_version = spark.version    
# print("Spark Version:", spark_version)  

# sc = spark.sparkContext
# def toJStringArray(arr):
#     jarr = sc._gateway.new_array(sc._jvm.java.lang.String, len(arr))
#     for i in range(len(arr)):
#         jarr[i] = arr[i]
#     return jarr

# t0 = time.time()
# # Usage example:
# java_arr = toJStringArray(['string1'])

# # Load the Scala function  
# scala_function = spark._jvm.RunIsar.RepHammer.main

# # Use the Scala function  
# scala_function(java_arr)
# t1 = time.time()
# print('time cost %s' %(t1-t0))