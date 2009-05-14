package scalanlp.classify;

import scalanlp.util.Index;
import scalanlp.data._;
import scalanlp.counters._;
import Counters._

import scalala.tensor.dense._;
import scalala.Scalala._;

@serializable
@SerialVersionUID(1L)
class LinearClassifier[L,F](val featureIndex: Index[F], 
                            val labels: Seq[L],
                            val featureWeights: Array[Array[Double]],
                            val intercepts: Array[Double]) 
                            extends Classifier[L,Map[F,Double]] {
  def scores(o: Map[F,Double]) = {
    val c = DoubleCounter[L]();
    for( (l,i) <- labels.elements.zipWithIndex) {
      val lWeights = featureWeights(i);
      var score = intercepts(i);
      for( (f,v) <- o;
           fi <- featureIndex.indexOpt(f) ) {
        score += lWeights(fi) * v; 
      }
      c(l) = score;
    }
    c
  }
}

object LinearClassifier {
  def fromRegression[F](data: Collection[Example[Boolean,Map[F,Double]]]) = {
    val featureIndex = Index[F]();
    val idata = ( for(e <- data.elements) yield { 
        for( map <- e;
             fv <- map) yield {
        (featureIndex(fv._1),fv._2);
      }
    } ).collect

    val vdata = new DenseMatrix(data.size,featureIndex.size+1);
    val y = new DenseVector(data.size);
    for( (e,i) <- idata.elements.zipWithIndex ) {
      y(i) = if(e.label) 1 else -1; 
      vdata(i,featureIndex.size) = 1;
      for( (f,v) <- e.features) {
        vdata(i,f) = v;
      }
    }

    val lI = DenseMatrix(featureIndex.size+1,featureIndex.size+1)();
    lI := diag(featureIndex.size+1) * 1E-6;

    val xtx = lI;
    xtx :+= vdata.transpose * vdata;

    val xty = vdata.transpose * y value;
    val beta = xtx \ xty;
    val betaArray = beta.values.collect.toArray;
    val trueWeights = betaArray.take(betaArray.size-1).toArray;
    val falseWeights = Array.make(trueWeights.size,0.0);
    new LinearClassifier(featureIndex,List(true,false),Array(trueWeights,falseWeights),Array(betaArray.last,0));
  }

  def testLR = {
    import stats.sampling.Rand;
    
    val trueDataGen = for { 
      x <- Rand.gaussian(4,1);
      y <- Rand.gaussian(-3,1)
    } yield {
      Example(true, Map(1->x,2->y));
    }

    val falseDataGen = for { 
      x <- Rand.gaussian(-4,1);
      y <- Rand.gaussian(2,1)
    } yield {
      Example(false, Map(1->x,2->y));
    }

    val data = trueDataGen.sample(1000) ++ falseDataGen.sample(1000);

    LinearClassifier.fromRegression(data);
  }
}