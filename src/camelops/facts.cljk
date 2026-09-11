(ns camelops.facts
  "Reference facts for camelid-raising operations coordination: supply
  category cost policy and herd species classification. This namespace
  contains pure lookup functions for domain reference data -- the Governor
  and Advisor consult these instead of inventing thresholds. Mirrors
  `cattleops.facts` (cloud-itonami-isic-0141) in shape.")

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (rancher/herder/ops-manager)."
  {"feed"
   {:id "feed" :name "飼料" :cost-threshold 500}

   "veterinary-supply"
   {:id "veterinary-supply" :name "獣医用品" :cost-threshold 500}

   "fiber-processing-supply"
   {:id "fiber-processing-supply" :name "繊維加工用品" :cost-threshold 500}

   "equipment"
   {:id "equipment" :name "設備" :cost-threshold 1000}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def species
  "Species this actor's facility/herd records may cover (ISIC 0143: camels
  and camelids -- dairy, fiber, and pack-animal use cases)."
  {"dromedary-camel" {:id "dromedary-camel" :name "ヒトコブラクダ"}
   "bactrian-camel"  {:id "bactrian-camel" :name "フタコブラクダ"}
   "llama"           {:id "llama" :name "リャマ"}
   "alpaca"          {:id "alpaca" :name "アルパカ"}})

(defn species-by-id [id]
  (get species id))

(def use-classes
  "Primary use classifications a herd record's animal(s) may be kept for.
  A single animal/herd may serve more than one; this is reference data
  for logging, not an exclusivity constraint."
  {"dairy"       {:id "dairy" :name "乳用"}
   "fiber"       {:id "fiber" :name "繊維用"}
   "pack-animal" {:id "pack-animal" :name "荷役用"}})

(defn use-class-by-id [id]
  (get use-classes id))
