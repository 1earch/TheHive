package org.thp.thehive.services

import gremlin.scala._
import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.auth.AuthContext
import org.thp.scalligraph.models._
import org.thp.scalligraph.services._
import org.thp.scalligraph.{CreateError, EntitySteps}
import org.thp.thehive.models._

import scala.util.{Failure, Try}

@Singleton
class ShareSrv @Inject()(implicit val db: Database) extends VertexSrv[Share, ShareSteps] {

  val organisationShareSrv = new EdgeSrv[OrganisationShare, Organisation, Share]
  val shareProfileSrv      = new EdgeSrv[ShareProfile, Share, Profile]
  val shareCaseSrv         = new EdgeSrv[ShareCase, Share, Case]
  val shareTaskSrv         = new EdgeSrv[ShareTask, Share, Task]
  val shareObservableSrv   = new EdgeSrv[ShareObservable, Share, Observable]

  override def steps(raw: GremlinScala[Vertex])(implicit graph: Graph): ShareSteps = new ShareSteps(raw)

  def create(`case`: Case with Entity, organisation: Organisation with Entity, profile: Profile with Entity)(
      implicit graph: Graph,
      authContext: AuthContext
  ): Try[Share with Entity] =
    if (get(`case`, organisation).profile.exists())
      Failure(CreateError(s"Case #${`case`.number} is already shared with organisation ${organisation.name}"))
    else
      for {
        createdShare <- createEntity(Share())
        _            <- organisationShareSrv.create(OrganisationShare(), organisation, createdShare)
        _            <- shareCaseSrv.create(ShareCase(), createdShare, `case`)
        _            <- shareProfileSrv.create(ShareProfile(), createdShare, profile)
      } yield createdShare

  def get(`case`: Case with Entity, organisation: Organisation with Entity)(implicit graph: Graph): ShareSteps =
    initSteps.relatedTo(`case`).relatedTo(organisation)
}

@EntitySteps[Share]
class ShareSteps(raw: GremlinScala[Vertex])(implicit db: Database, graph: Graph) extends BaseVertexSteps[Share, ShareSteps](raw) {
  override def newInstance(raw: GremlinScala[Vertex]): ShareSteps = new ShareSteps(raw)

  def relatedTo(`case`: Case with Entity): ShareSteps = where(_.`case`.get(`case`._id))

  def relatedTo(organisation: Organisation with Entity): ShareSteps = where(_.organisation.get(organisation._id))

  def organisation: OrganisationSteps = new OrganisationSteps(raw.inTo[OrganisationShare])

  def tasks = new TaskSteps(raw.outTo[ShareTask])

  def observables = new ObservableSteps(raw.outTo[ShareObservable])

  def `case`: CaseSteps = new CaseSteps(raw.outTo[ShareCase])

  def profile: ProfileSteps = new ProfileSteps(raw.outTo[ShareProfile])
}
