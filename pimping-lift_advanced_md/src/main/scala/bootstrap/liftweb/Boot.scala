package bootstrap.liftweb

import net.liftweb._
import util._
import Helpers._

import common._
import http._
import js.jquery.JQueryArtifacts
import sitemap._
import Loc._
import mapper.{DB,StandardDBVendor,Schemifier}

import code.model._
import net.liftmodules.{FoBo}
import scravatar.{Gravatar,DefaultImage}


/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot {
  def boot {
    if (!DB.jndiJdbcConnAvailable_?) {
      val vendor = 
	new StandardDBVendor(Props.get("db.driver") openOr "org.h2.Driver",
			     Props.get("db.url") openOr 
			     "jdbc:h2:lift_proto.db;AUTO_SERVER=TRUE",
			     Props.get("db.user"), Props.get("db.password"))

      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
    }

    // Use Lift's Mapper ORM to populate the database
    // you don't need to use Mapper to use Lift... use
    // any ORM you want
    Schemifier.schemify(true, Schemifier.infoF _, User)

    // where to search snippet
    LiftRules.addToPackages("code")


    def sitemapMutators = User.sitemapMutator
    //The SiteMap is built in the Site object bellow 
    LiftRules.setSiteMapFunc(() => sitemapMutators(Site.sitemap))

    //Init the FoBo - Front-End Toolkit module, 
    //see http://liftweb.net/lift_modules for more info
    FoBo.InitParam.JQuery=FoBo.JQuery1113  
    FoBo.InitParam.ToolKit=FoBo.FontAwesome430 
    FoBo.InitParam.ToolKit=FoBo.AngularJS148 
    FoBo.InitParam.ToolKit=FoBo.AJMaterial100
    FoBo.init() 
    
    //Show the spinny image when an Ajax call starts
    LiftRules.ajaxStart =
      Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
    
    // Make the spinny image go away when it ends
    LiftRules.ajaxEnd =
      Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    // Force the request to be UTF-8
    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

    // What is the function to test if a user is logged in?
    LiftRules.loggedInTest = Full(() => User.loggedIn_?)

    // Use HTML5 for rendering
    LiftRules.htmlProperties.default.set((r: Req) =>
      new Html5Properties(r.userAgent))    
      
//    LiftRules.noticesAutoFadeOut.default.set( (notices: NoticeType.Value) => {
//        notices match {
//          case NoticeType.Notice => Full((8 seconds, 4 seconds))
//          case _ => Empty
//        }
//     }
//    ) 
    
    LiftRules.securityRules = () => {
      SecurityRules(content = Some(ContentSecurityPolicy(
        scriptSources = List(
            ContentSourceRestriction.UnsafeEval,
            ContentSourceRestriction.UnsafeInline,
            ContentSourceRestriction.Self),
        styleSources = List(
            ContentSourceRestriction.UnsafeInline,
            ContentSourceRestriction.Self),
        imageSources = List(            
            ContentSourceRestriction.All)
            
            )))
    } 
    
    // Make a transaction span the whole HTTP request
    S.addAround(DB.buildLoanWrapper)
  }
  
  object Site {
    import scala.xml._
    //if user is logged in replace menu label "User" with users gravatar image and full name.
    def userDDLabel:NodeSeq = { 
      def gravatar:NodeSeq = {
        val gurl = Gravatar(User.currentUser.map(u => u.email.get).openOrThrowException("Something wicked happened #1")).size(36).avatarUrl
        <img class="gravatar" src={gurl}/> 
      }      
      lazy val username = User.currentUser.map(u => u.firstName + " "+ u.lastName)
      User.loggedIn_? match {
        case true =>  <xml:group>{gravatar}  {username.openOrThrowException("Something wicked happened")}</xml:group> 
        case _ => <xml:group>{S ? "UserDDLabel"}</xml:group>   
      }
    }

    //val ddLabel1   = Menu(userDDLabel) / "ddlabel1"
    val divider1   = Menu("divider1") / "divider1"
    val home       = Menu.i("Home") / "index" 
    
    val userMenu   = User.AddUserMenusHere
    
    val static     = Menu(Loc("Static", 
        Link(List("static"), true, "/static/index"), 
        S.loc("StaticContent" , scala.xml.Text("Static Content")),
        LocGroup("lg2","topRight") ))
        
    val AMDesign       = Menu(Loc("AMDesign", 
        ExtLink("https://material.angularjs.org/"), 
        S.loc("AMDesign" , scala.xml.Text("Angular Material")),
        LocGroup("lg2")/*,
        FoBo.TBLocInfo.LinkTargetBlank */ ))   
     
    val FLTDemo       = Menu(Loc("FLTDemo", 
        ExtLink("http://www.media4u101.se/fobo-lift-template-demo/"), 
        S.loc("FLTDemo" , scala.xml.Text("FoBo Lift Template Demo")),
        LocGroup("lg2")/*,
        FoBo.TBLocInfo.LinkTargetBlank */ ))               
        
    def sitemap = SiteMap(
        home          >> LocGroup("lg1"),
        static,
        AMDesign,
        FLTDemo
//        ddLabel1
//        ddLabel1      >> LocGroup("topRight") >> PlaceHolder submenus (
//            divider1  /*>> FoBo.TBLocInfo.Divider*/ >> userMenu
//            )
         )
  }
  
}
