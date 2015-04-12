package edu.gemini.ags.gems

import edu.gemini.catalog.api.{SaturationConstraint, FaintnessConstraint, MagnitudeConstraints, RadiusConstraint}
import edu.gemini.catalog.votable.TestVoTableBackend
import edu.gemini.spModel.gemini.gems.Canopus.Wfs
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._
import edu.gemini.shared.util.immutable.{None => JNone}
import edu.gemini.spModel.core._
import edu.gemini.spModel.core.AngleSyntax._
import edu.gemini.spModel.gemini.gems.GemsInstrument
import edu.gemini.spModel.gemini.gsaoi.{GsaoiOdgw, Gsaoi}
import edu.gemini.spModel.gemini.obscomp.SPSiteQuality
import edu.gemini.spModel.gems.{GemsGuideStarType, GemsTipTiltMode}
import edu.gemini.spModel.obs.context.ObsContext
import edu.gemini.spModel.target.SPTarget
import edu.gemini.spModel.target.env.TargetEnvironment
import edu.gemini.spModel.telescope.IssPort
import AlmostEqual.AlmostEqualOps

import org.specs2.mutable.Specification

import scalaz._
import Scalaz._

import scala.concurrent.Await
import scala.collection.JavaConverters._

class GemsVoTableCatalogSpec extends Specification with NoTimeConversions {
  val magnitudeConstraints = MagnitudeConstraints(MagnitudeBand.J, FaintnessConstraint(10.0), SaturationConstraint(2.0).some)

  "GemsVoTableCatalog" should {
    "support executing queries" in {
      val ra = Angle.fromHMS(3, 19, 48.2341).getOrElse(Angle.zero)
      val dec = Angle.fromDMS(41, 30, 42.078).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Gsaoi
      inst.setPosAngle(0.0)
      inst.setIssPort(IssPort.SIDE_LOOKING)
      val ctx = ObsContext.create(env, inst, JNone.instance[Site], SPSiteQuality.Conditions.BEST, null, null)
      val base = Coordinates(RightAscension.fromAngle(ra), Declination.fromAngle(dec).getOrElse(Declination.zero))
      val opticalCatalog = GemsGuideStarSearchOptions.DEFAULT_CATALOG
      val nirCatalog = GemsGuideStarSearchOptions.DEFAULT_CATALOG
      val instrument = GemsInstrument.gsaoi
      val tipTiltMode = GemsTipTiltMode.instrument

      val posAngles = new java.util.HashSet[Angle]()
      val options = new GemsGuideStarSearchOptions(opticalCatalog, nirCatalog,
              instrument, tipTiltMode, posAngles)

      val results = Await.result(GemsVoTableCatalog(TestVoTableBackend("/gemsvotablecatalogquery.xml")).search(ctx, base, options, scala.None, null), 30.seconds)
      results should be size 2

      results(0).criterion should beEqualTo(GemsCatalogSearchCriterion(GemsCatalogSearchKey(GemsGuideStarType.tiptilt, GsaoiOdgw.Group.instance), CatalogSearchCriterion("On-detector Guide Window tiptilt", MagnitudeConstraints(MagnitudeBand.H, FaintnessConstraint(14.5), Some(SaturationConstraint(7.3))).some, RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), Some(Offset((0.0014984027777700248).degrees[OffsetP], (0.0014984027777700248).degrees[OffsetQ])), scala.None)))
      results(1).criterion should beEqualTo(GemsCatalogSearchCriterion(GemsCatalogSearchKey(GemsGuideStarType.flexure, Wfs.Group.instance), CatalogSearchCriterion("Canopus Wave Front Sensor flexure", MagnitudeConstraints(MagnitudeBand.R, FaintnessConstraint(16.0), Some(SaturationConstraint(8.5))).some, RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01666666666665151)), Some(Offset((0.0014984027777700248).degrees[OffsetP], (0.0014984027777700248).degrees[OffsetQ])), scala.None)))
      results(0).results should be size 5
      results(1).results should be size 3
    }.pendingUntilFixed
    "calculate the optimal radius limit" in {
      val ra = Angle.fromHMS(3, 19, 48.2341).getOrElse(Angle.zero)
      val dec = Angle.fromDMS(41, 30, 42.078).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Gsaoi
      inst.setPosAngle(0.0)
      inst.setIssPort(IssPort.SIDE_LOOKING)
      val ctx = ObsContext.create(env, inst, JNone.instance[Site], SPSiteQuality.Conditions.BEST, null, null)
      val opticalCatalog = GemsGuideStarSearchOptions.DEFAULT_CATALOG
      val nirCatalog = GemsGuideStarSearchOptions.DEFAULT_CATALOG
      val instrument = GemsInstrument.gsaoi
      val tipTiltMode = GemsTipTiltMode.instrument

      val posAngles = new java.util.HashSet[Angle]()
      val options = new GemsGuideStarSearchOptions(opticalCatalog, nirCatalog,
              instrument, tipTiltMode, posAngles)

      val results = GemsVoTableCatalog(TestVoTableBackend("/gemsvotablecatalogquery.xml")).getRadiusLimits(instrument, options.searchCriteria(ctx, scala.None).asScala.toList)
      results should be size 1
      results(0) should beEqualTo(RadiusConstraint.between(Angle.zero, Angle.fromDegrees(0.01878572819686042)))
    }
    "calculate the optimal magnitude limit" in {
      val ra = Angle.fromHMS(3, 19, 48.2341).getOrElse(Angle.zero)
      val dec = Angle.fromDMS(41, 30, 42.078).getOrElse(Angle.zero)
      val target = new SPTarget(ra.toDegrees, dec.toDegrees)
      val env = TargetEnvironment.create(target)
      val inst = new Gsaoi
      inst.setPosAngle(0.0)
      inst.setIssPort(IssPort.SIDE_LOOKING)
      val ctx = ObsContext.create(env, inst, JNone.instance[Site], SPSiteQuality.Conditions.BEST, null, null)
      val opticalCatalog = GemsGuideStarSearchOptions.DEFAULT_CATALOG
      val nirCatalog = GemsGuideStarSearchOptions.DEFAULT_CATALOG
      val instrument = GemsInstrument.gsaoi
      val tipTiltMode = GemsTipTiltMode.instrument

      val posAngles = new java.util.HashSet[Angle]()
      val options = new GemsGuideStarSearchOptions(opticalCatalog, nirCatalog,
              instrument, tipTiltMode, posAngles)

      val results = GemsVoTableCatalog(TestVoTableBackend("/gemsvotablecatalogquery.xml")).optimizeMagnitudeLimits(options.searchCriteria(ctx, scala.None).asScala.toList)
      results should be size 2
      results(0) should beEqualTo(MagnitudeConstraints(MagnitudeBand.R, FaintnessConstraint(16), Some(SaturationConstraint(8.5))))
      results(1) should beEqualTo(MagnitudeConstraints(MagnitudeBand.H, FaintnessConstraint(14.5), Some(SaturationConstraint(7.3))))
    }
    "preserve the radius constraint for a single item without offsets" in {
      val catalog = GemsVoTableCatalog(TestVoTableBackend(""))
      val key = new GemsCatalogSearchKey(GemsGuideStarType.flexure, GsaoiOdgw.Group.instance)
      val radiusConstraint = RadiusConstraint.between(Angle.fromArcmin(10.0), Angle.fromArcmin(2.0))
      val criterion = CatalogSearchCriterion("test", magnitudeConstraints.some, radiusConstraint, None, None)

      val s = new GemsCatalogSearchCriterion(key, criterion)
      catalog.optimizeRadiusConstraint(List(s).asJava).maxLimit ~= radiusConstraint.maxLimit
      catalog.optimizeRadiusConstraint(List(s).asJava).minLimit ~= radiusConstraint.minLimit
    }
    "offset the radius constraint for a single item with offsets" in {
      val catalog = GemsVoTableCatalog(TestVoTableBackend(""))
      val key = new GemsCatalogSearchKey(GemsGuideStarType.flexure, GsaoiOdgw.Group.instance)
      val radiusConstraint = RadiusConstraint.between(Angle.fromArcmin(10.0), Angle.fromArcmin(2.0))
      val offset = Offset(3.arcmins[OffsetP], 4.arcmins[OffsetQ]).some
      val posAngle = Angle.fromArcmin(3).some
      val criterion = CatalogSearchCriterion("test", magnitudeConstraints.some, radiusConstraint, offset, posAngle)

      val s = new GemsCatalogSearchCriterion(key, criterion)
      catalog.optimizeRadiusConstraint(List(s).asJava).maxLimit ~= radiusConstraint.maxLimit + Angle.fromArcmin(5)
      catalog.optimizeRadiusConstraint(List(s).asJava).minLimit ~= radiusConstraint.minLimit
    }
    "find the max and min for a list of radius constraint without offsets" in {
      val catalog = GemsVoTableCatalog(TestVoTableBackend(""))
      val key = new GemsCatalogSearchKey(GemsGuideStarType.flexure, GsaoiOdgw.Group.instance)
      val radiusConstraint1 = RadiusConstraint.between(Angle.fromArcmin(10.0), Angle.fromArcmin(2.0))
      val radiusConstraint2 = RadiusConstraint.between(Angle.fromArcmin(15.0), Angle.fromArcmin(3.0))
      val criterion1 = CatalogSearchCriterion("test", magnitudeConstraints.some, radiusConstraint1, None, None)
      val criterion2 = CatalogSearchCriterion("test", magnitudeConstraints.some, radiusConstraint2, None, None)

      val s1 = new GemsCatalogSearchCriterion(key, criterion1)
      val s2 = new GemsCatalogSearchCriterion(key, criterion2)
      catalog.optimizeRadiusConstraint(List(s1, s2).asJava).maxLimit ~= Angle.fromArcmin(15.0)
      catalog.optimizeRadiusConstraint(List(s1, s2).asJava).minLimit ~= Angle.fromArcmin(2.0)
    }
    "find the max and min for a list of radius constraints with offsets" in {
      val catalog = GemsVoTableCatalog(TestVoTableBackend(""))
      val key = new GemsCatalogSearchKey(GemsGuideStarType.flexure, GsaoiOdgw.Group.instance)
      val radiusConstraint1 = RadiusConstraint.between(Angle.fromArcmin(10.0), Angle.fromArcmin(2.0))
      val radiusConstraint2 = RadiusConstraint.between(Angle.fromArcmin(15.0), Angle.fromArcmin(3.0))

      val offset1 = Offset(3.arcmins[OffsetP], 4.arcmins[OffsetQ]).some
      val offset2 = Offset(5.arcmins[OffsetP], 12.arcmins[OffsetQ]).some
      val posAngle = Angle.fromArcmin(3).some
      val criterion1 = CatalogSearchCriterion("test", magnitudeConstraints.some, radiusConstraint1, offset1, posAngle)
      val criterion2 = CatalogSearchCriterion("test", magnitudeConstraints.some, radiusConstraint2, offset2, posAngle)

      val s1 = new GemsCatalogSearchCriterion(key, criterion1)
      val s2 = new GemsCatalogSearchCriterion(key, criterion2)
      // Gets the offset from the largest offset distance (offset2 in this case)
      catalog.optimizeRadiusConstraint(List(s1, s2).asJava).maxLimit ~= (Angle.fromArcmin(15.0) + Angle.fromArcmin(13))
      catalog.optimizeRadiusConstraint(List(s1, s2).asJava).minLimit ~= Angle.fromArcmin(2.0)
    }
  }
}