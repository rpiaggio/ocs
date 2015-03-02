package edu.gemini.sp.vcs.diff

import edu.gemini.pot.sp.ISPProgram
import edu.gemini.pot.sp.version.VersionMap
import edu.gemini.sp.vcs.log.VcsEventSet
import edu.gemini.spModel.core.SPProgramID


/** Public interface for VCS service. Defines the API for low-level inter-JVM
  * operations that are conducted over trpc.  There is a server implementation
  * in [[edu.gemini.sp.vcs.diff.VcsServer]] and a trpc client in
  * [[edu.gemini.sp.vcs.diff.Vcs]]. */
trait VcsService {

  /** Fetches the `VersionMap`. */
  def version(id: SPProgramID): TryVcs[VersionMap]

  /** Add the given program, copying it to the remote database. */
  def add(p: ISPProgram): TryVcs[Unit]

  /** Checkout the corresponding program, copying it to the local database. */
  def checkout(id: SPProgramID): TryVcs[ISPProgram]

  /** Gets the `VersionMap` and the set of `SPNodeKey` that correspond to
    * deleted nodes. */
  def diffState(id: SPProgramID): TryVcs[DiffState]

  /** Obtains remote differences based on the provided local diff state. */
  def fetchDiffs(id: SPProgramID, ds: DiffState): TryVcs[MergePlan.Transport]

  /** Applies the given `MergePlan` to the remote program, returning `true`
    * if the program is actually updated; `false` otherwise. */
  def storeDiffs(id: SPProgramID, mp: MergePlan.Transport): TryVcs[Boolean]

  /** Fetches a chunk of the vcs log. */
  def log(p: SPProgramID, offset:Int, length:Int): TryVcs[(List[VcsEventSet], Boolean)]
}
