(ns camelops.operation
  "OperationActor -- one camelid-raising operation = one supervised actor
  run, expressed as a REAL compiled `langgraph-clj` `StateGraph`
  (`langgraph.graph/state-graph` + `compile-graph`). The advisor
  (CamelOpsAdvisor) is sealed into a single node (`:advise`); its
  proposal is ALWAYS routed through the independent Camelid Facility
  Operations Governor (`camelops.governor/check`, `:govern`) and the
  rollout phase gate (`camelops.phase/gate`, `:decide`) before anything
  commits to the SSoT.

  This replaces the previous `run-operation`, a plain sequential
  advisor -> governor -> phase-gate -> disposition function pipeline
  that never required `langgraph.graph` and never touched
  `state-graph`/`add-node`/`compile-graph` at all -- despite this
  namespace's own former docstring stating \"NOTE: langgraph-clj
  StateGraph integration is deferred... This stub version defines the
  high-level flow synchronously\". `deps.edn`'s top-level `:deps` was
  also literally `{}` -- the real `io.github.kotoba-lang/langgraph`
  dependency only existed under an unused `:dev :override-deps` alias,
  so even the deferred-stub docstring's own stated intent (\"production
  build wires this into a langgraph-clj StateGraph\") could never
  actually resolve on the classpath any build actually used. Both gaps
  are now closed: `deps.edn`'s top-level `:deps` carries the real
  dependency, and this namespace is a genuinely compiled StateGraph. No
  audit ledger existed anywhere in this repo either -- `camelops.store`
  now has a real append-only `ledger`/`append-ledger!`, wired into this
  graph's terminal nodes.

  State machine:
  intake -> advise -> govern -> decide -+-> commit
                                         +-> request-approval -> commit
                                         +-> hold

  Everything the actor depends on is injected, so each is a swap, not a
  rewrite:
    - the Store    (`camelops.store/MemStore`, or any `Store` impl)
    - the Advisor  (mock today; `camelops.advisor/Advisor` is already
                     the injection point -- see its docstring)
    - the Phase    (0->3 rollout; carried per-request on `:context`'s
                     `:phase` key -- matches the old `run-operation`'s
                     call-time `context` argument, not frozen at `build`
                     time)

  One graph run = one camelid-raising operation. No unbounded inner
  loop -- each run is auditable and checkpointed. A facility's operating
  history is advanced by MANY operations (log-herd-record /
  schedule-veterinary-visit / flag-animal-health-concern /
  order-supplies), each its own independent run. Every
  commit/hold/approval-rejected decision fact lands in
  `camelops.store`'s append-only ledger (`store/append-ledger!`), now
  reachable from the `:commit`, `:hold`, and `:request-approval`
  (rejection) nodes.

  `camelops.governor`'s hard/soft checks and `camelops.phase`'s 0->3
  rollout gate are reused UNCHANGED -- this fix only wires the existing
  domain policy into a real compiled graph and a real ledger, it does
  not redesign the camelid-raising compliance rules (facility
  registration, closed op allowlist, treatment/slaughter exclusion, cost
  threshold, herd-count/fiber-yield plausibility, always-escalate
  health concerns).

  Human-in-the-loop = real approval workflow:
  `interrupt-before #{:request-approval}` pauses the actor at the
  `:request-approval` node until a human rancher/herder/veterinarian
  resumes it with a decision. `:flag-animal-health-concern` ALWAYS
  reaches this node when the Governor is clean -- see
  `camelops.governor/always-escalate-ops`. `camelops.phase`'s phase-0
  ALSO routes every otherwise-committable proposal through this node
  (no autonomous commits during simulation/test rollout) -- this
  matches the pre-graph behavior exactly (`phase/gate`'s `:phase-0`
  case turns a would-be `:commit` into `:escalate`, never `:hold`)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [camelops.advisor :as advisor]
            [camelops.governor :as governor]
            [camelops.phase :as phase]
            [camelops.store :as store]))

;; ============================================================================
;; Audit-fact builders
;; ============================================================================

(defn- commit-fact
  "The audit fact written when a proposal commits. `:proposal` carries the
  full advisor proposal (herd-record/veterinary-visit/health-concern/
  supply-order data) -- camelops has no separate stateful commit-record!
  entity beyond the facility directory, so the ledger fact itself is the
  durable record of what happened."
  [request context proposal approval]
  (cond-> {:t           :committed
           :op          (:op request)
           :actor       (:actor-id context)
           :subject     (:facility-id request)
           :disposition :commit
           :basis       (:cites proposal)
           :summary     (:summary proposal)
           :proposal    proposal}
    approval (assoc :approved-by (:by approval))))

(defn- commit-record
  "Shape of the committed-proposal record -- preserved unchanged from the
  pre-graph `run-operation`'s `commit-record`. Not persisted by this
  namespace (camelops has no stateful commit-record! entity beyond the
  facility directory itself); exposed on the `:record` channel for
  callers that want the applied value, same as before."
  [request _context proposal]
  {:effect  (:effect proposal)
   :path    [(:facility-id request)]
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

;; ============================================================================
;; Compiled StateGraph
;; ============================================================================

(defn build
  "Compiles an OperationActor graph bound to `store`. opts:
    :advisor      -- a `camelops.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- a `langgraph.checkpoint/Checkpointer`
                     (default: in-memory `cp/mem-checkpointer`)

  The compiled graph's input map: `{:request .. :context ..}` -- `context`
  carries `:actor-id`/`:role`/`:phase`, matching the old `run-operation`'s
  call-time `context` argument (phase is per-request, not frozen at
  `build` time)."
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default {}}
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [proposal (advisor/-advise advisor store request)]
            {:proposal proposal
             :audit    [(advisor/trace request proposal)]})))

      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      (g/add-node :decide
        (fn [{:keys [request context verdict]}]
          (let [base-disposition (phase/verdict->disposition verdict)
                ph               (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base-disposition)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t          :approval-requested
                        :op         (:op request)
                        :actor      (:actor-id context)
                        :subject    (:facility-id request)
                        :reason     (or reason
                                        (cond (:high-stakes? verdict) :always-escalate
                                              :else :low-confidence))
                        :phase      ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit}))))

      (g/add-node :request-approval
        (fn [{:keys [request context approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :audit [{:t       :approval-granted
                      :op      (:op request)
                      :actor   (:actor-id context)
                      :subject (:facility-id request)
                      :by      (:by approval)}]}
            {:disposition :hold
             :audit [(assoc (governor/hold-fact request context verdict)
                            :t :approval-rejected
                            :phase-reason :approver-rejected
                            :by (:by approval))]})))

      (g/add-node :commit
        (fn [{:keys [request context proposal approval]}]
          (let [f      (commit-fact request context proposal approval)
                record (commit-record request context proposal)]
            (store/append-ledger! store f)
            {:audit [f] :record record})))

      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store hf))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
