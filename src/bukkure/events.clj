;; TODO: Check this file manually
(ns bukkure.events
  "Event handlers for bukkit"
  (:require [bukkure.logging :as log]
            [bukkure.util :as util]
            [bukkure.bukkit :as bk]
            [bukkure.player :as plr]))

(defonce actions (util/map-enums org.bukkit.event.block.Action))
(defonce priorities (util/map-enums org.bukkit.event.EventPriority))

(defn handle-event
  "A small wrapper around an event, to allow return values"
  [f e]
  (if-let [response (f e)]
    (do
      (if (:msg response) (plr/send-msg e (:msg response))))))

(defrecord IdListener
  [id]
  org.bukkit.event.Listener)

(defn register-event
  "Registers an event to a plugin

  example eventnames: player.player-quit for player.PlayerQuit

  id: A unique id for this event. Allows redefining this event."
  [plugin eventname f {:keys [priority-key id] :as opts}]
  (let [eventclass (resolve (symbol (util/package-classname "org.bukkit.event" (str eventname "-event"))))]
    (when id
      (doseq [listener
              (->> (org.bukkit.event.HandlerList/getRegisteredListeners plugin)
                   (map (memfn getListener))
                   (filter #(and
                              (instance? IdListener %)
                              (= id (:id %)))))]
        (org.bukkit.event.HandlerList/unregisterAll listener)))
    (.registerEvent
      (bk/plugin-manager)
      eventclass
      (if id
        (map->IdListener {:id id})
        (proxy [org.bukkit.event.Listener] []))
      (get priorities (or priority-key :normal))
      (proxy [org.bukkit.plugin.EventExecutor] []
        (execute [l e] (handle-event f e)))
      plugin)))

(defn ^:deprecated register-eventlist
  "Register a list of events."
  [plugin events]
  (doseq [ev events]
    (register-event plugin (:eventname ev) (:event-fn ev) (:priority ev))))

(defn event
  "Convenience function for registering events, eventname being prefixed with org.bukkit.event. 
and camelcased so that you can simply call (onevent block.block-break-event [e] (logging/info (bean e))) 
to register for the org.bukkit.event.block.BlockBreakEvent and run the body with the BlockBreakEvent as its only 
argument"
  [eventname fn & [priority]]
  {:eventname eventname
    :event-fn fn
    :priority priority})

(defn find-event
  "Finds an event by it's name"
  [name]
  (let [classes (util/find-subclasses "org.bukkit" org.bukkit.event.Event)
        names (map #(.replaceAll
                     (.replaceAll (util/class-named %) "org.bukkit.event." "")
                     "-event$" "") classes)]
    (filter #(.contains % (.toLowerCase name)) names)))

(def boring-methods
  "Boring methods to hide when describing an event"
  #{"getHandlers" "getHandlerList" "wait" "equals" "toString" "hashCode" "getClass" "notify" "notifyAll" "isAsynchronous"})

(defn describe-event
  "Returns a list of methods an event has, excluding [[boring-methods]]"
  [eventname]
  (let [classname (util/package-classname "org.bukkit.event" (str eventname "-event"))
        cl (resolve (symbol classname))]
    (set
     (filter #(not (contains? boring-methods %))
             (map #(:name (bean %))  (seq (:methods (bean cl))))))))
