package pageRank

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.SparkContext._;

import wikiParser.GraphGenerator;

object PageRank {
  
  case class PageNode(pageRank:Double,adjList:List[String])
  	
  implicit val sortTupleByDoubleAndString = new Ordering[(String, Double)] {
    override def compare(a: (String, Double), b: (String, Double)) = {
      if (a._2 > b._2) {
        1
      } else if (a._2 < b._2) {
        -1
      } else {
        b._1.compareTo(a._1)
      }
    }
  }
  
  def emitPageNode(node: String) : List[(String, List[String])] = {
    val pageArray = node.split("~~~")
    val pageName = pageArray(0)
    val outlinks = if (pageArray.size > 1) pageArray(1).split("~").toList else List[String]()
    val pageNode = List((pageName, outlinks))
    val adjNodes = outlinks.map(node => (node, List[String]()))
    pageNode ::: adjNodes
  }
  
  def distributeContribution(node: (String, PageNode)) : List[(String, PageNode)] = {
    val pageName = node._1
    val adjList = node._2.adjList
    val contribution = node._2.pageRank / adjList.length
    val nodeStructure = List((pageName, PageNode(0.0, adjList)))
    val contributionList = adjList.map(adjNode => (adjNode, PageNode(contribution, List[String]())))
    nodeStructure ::: contributionList
  }
  
  def accumulateContribution(node1: PageNode, node2: PageNode) : PageNode = {
    PageNode(node1.pageRank + node2.pageRank, node1.adjList ::: node2.adjList)
  }
  
  def main(args: Array[String]) = {
    
    // Parse args
    val input = args(0)
    val output = args(1)
    val k = Integer.valueOf(args(2))

    //Start the Spark context
    val conf = new SparkConf()
      .setAppName("Page Rank")
      .setMaster("local[*]")
    val sc = new SparkContext(conf)

    // Load input file as RDD
    val lines = sc.textFile(input)
    .map(line => GraphGenerator.createGraph(line))
    .filter(line => line != null) // Filter out invalid pages
    .persist()
    
    // Pre-Process on initial graph
    val graph = lines
    .flatMap(emitPageNode)
    .reduceByKey((adjList1, adjList2) => adjList1 ::: adjList2)
    .persist()
    
    // Count total valid page and add initial pagerank
    val pageCount = graph.count()
    println(s"[DEBUG] PAGE COUNT: ${pageCount}")
    val initPageRank = 1.0 / pageCount
    var pageNodes = graph
    .map(pageNode => (pageNode._1, PageNode(initPageRank, pageNode._2)))
    
    // 10 times of Pagerank job
    for ( i <- 1 to 10 ) {
      println(s"[DEBUG] LOOP ${i}")
      
      // Calculate delta sum (pagerank sum of dangling nodes)
      val deltaSum = pageNodes
      .filter(node => node._2.adjList.length == 0)
      .aggregate(0.0)((curSum, node) => curSum + node._2.pageRank, (sum1, sum2) => sum1 + sum2)
      println(s"[DEBUG] DELTA SUM: ${deltaSum}")
        
      // Distribute  and accumulate contributions 
      pageNodes = pageNodes
      .flatMap(distributeContribution)
      .reduceByKey(accumulateContribution)
      .mapValues(node => PageNode(0.15 / pageCount + 0.85 * (node.pageRank + deltaSum / pageCount), node.adjList))     
      
      val pageRankSum = pageNodes
      .aggregate(0.0)((curSum, node) => (curSum + node._2.pageRank), (sum1, sum2) => sum1 + sum2)
      println(s"[DEBUG] PAGE RANK SUM: ${pageRankSum}")
    }
    
    // Top K job
    val topK = pageNodes
    .map(node => (node._1, node._2.pageRank))
    .top(k)
    
    // Save as text file
    sc.parallelize(topK, 1).saveAsTextFile(output)
    
    //Stop the Spark context  
    sc.stop
  }
}