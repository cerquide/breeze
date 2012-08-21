package breeze.inference.bp

import breeze.linalg._
import breeze.numerics._
import breeze.util.Encoder

/**
 * Implements basic belief propagation for computing variable
 * marginals in graphical models.
 *
 * For more powerful stuff, you should probably use Factorie.
 * This is--imho--easier to use for "simple" problems.
 *
 * @author dlwh
 */
object BeliefPropagation {

  /**
   * The result object for BeliefPropagation, useful for getting information
   * about marginals and edge marginals
   * @param model the Factor model used to perform inference
   * @param beliefs
   * @param messages
   */
  case class Beliefs(model: Model,
                     beliefs: IndexedSeq[DenseVector[Double]],
                     messages: IndexedSeq[IndexedSeq[DenseVector[Double]]],
                     factorLogPartitions: IndexedSeq[Double]) {
    def marginalFor[T](v: Variable[T]): Counter[T, Double] = Encoder.fromIndex(v.domain).decode(beliefs(model.variableIndex(v)))


    /**
     * returns a factor representing the factor marginal for the given marginal.
     * That is, f(assignment) will give the marginal probability of any given assignment.
     *
     * If the factor is not in the original model, this still works, but it
     * doesn't mean much unless logApply returns 0.0 for all values.
     * @param f the factor
     * @return the edge marginal factor
     */
    def factorMarginalFor(f: Factor): Factor = new Factor {
      def variables = f.variables
      val (myMessages, myFVbyI) = {
        val fi = model.factorIndex(f)
        if(fi == -1)  f.variables.map { v => DenseVector.zeros[Double](v.domain.size) } -> f.variables.map(model.variableIndex).toArray
        else messages(fi) -> model.factorVariablesByIndices(fi)
      }



      def logApply(assignments: Array[Int]) = {
        f.logApply(assignments)* {
          for ( (v, i) <- myFVbyI.zipWithIndex) yield {
            beliefs(v)(assignments(i)) / math.exp(myMessages(i)(assignments(i)))
          }
        }.product

      }
    }

    val logPartition = factorLogPartitions.sum
  }

  /**
   * Performs inference on the model, giving a Beliefs object with marginals
   * @param model
   * @param maxIterations
   * @param tolerance
   * @return
   */
  def infer(model: Model, maxIterations: Int = 10, tolerance: Double = 1E-4) = {
    val beliefs = model.variables.map{ v =>
      val b = DenseVector.ones[Double](v.domain.size)
      b /= b.size.toDouble
      b
    }

    val messages = model.factors.map{ f =>
      f.variables.map { v => DenseVector.zeros[Double](v.domain.size) }
    }

    val partitions =  new Array[Double](model.factors.size)

    var converged = false
    var iter = 0

    while(!converged && iter < maxIterations) {
      converged = true
      for(f <- 0 until model.factors.length) {
        val divided = for( (v, m_fv) <- model.factorVariablesByIndices(f) zip messages(f)) yield {
          exp(log(beliefs(v)) - m_fv)
        }

        val newBeliefs = divided.map(b => DenseVector.zeros[Double](b.size))

        // send messages to all variables
        // this is actually the EP update, but whatever.
        var partition = 0.0
        model.factors(f).foreachAssignment { ass =>
          val score = model.factors(f).apply(ass) * {for ( (ass_i, b) <- ass zip divided) yield b(ass_i)}.product
          partition += math.log(score)

          for( (ass_i, bNew) <- ass zip newBeliefs) {
            bNew(ass_i) += score
          }

        }

        // normalize new beliefs
        newBeliefs foreach { b => b /= sum(b)}
        // compute new messages, store new beliefs in old beliefs
        for( ((v, mfv),  nb) <- model.factorVariablesByIndices(f) zip messages(f) zip newBeliefs) {
          mfv := (log(nb) - log(beliefs(v)))

          // nans are usually from infinities or division by 0.0, usually we can s
          for(i <- 0 until mfv.length) { if(mfv(i).isNaN) mfv(i) = 0.0}
          beliefs(v) := nb
        }

        partitions(f) = partition

        converged &&= (newBeliefs zip beliefs).forall { case (a,b) => norm(a - b, inf) <= 1E-4}
      }

      iter += 1
    }

    new Beliefs(model, beliefs, messages, partitions)
  }
}
