package rillit

import language.experimental.macros
import language.dynamics
import scala.reflect.macros._

class Lenser[T] extends Dynamic {
  def selectDynamic(propName: String)  = macro Lenser.selectDynamic[T]
}

object Lenser {
  def create[T]: Lenser[T] = new Lenser[T]

  def selectDynamic[T: c.WeakTypeTag](c: Context)(propName: c.Expr[String]) =
    applyDynamic[T](c)(propName)()

  def applyDynamic[T: c.WeakTypeTag](c: Context)(propName: c.Expr[String])() = {
    import c.universe._

    def abort(reason: String) = c.abort(c.enclosingPosition, reason)
    def mkParam(name: String, tpe: Type) =
      ValDef(Modifiers(Flag.PARAM), newTermName(name), TypeTree(tpe), EmptyTree)

    import treeBuild._
    val t = (c.prefix.tree, propName.tree) match {
      case (Apply(Select(New(tree: TypeTree), nme.CONSTRUCTOR),_), Literal(Constant(name: String))) =>
        tree.original match {
          case AppliedTypeTree(_, List(tpe)) => createLens(c)(tpe.tpe, name)
          case _                             => abort("No inner type found")
        }
      case x =>
        c.abort(c.enclosingPosition, "unexpected c.prefix tree: " + x)
    }
    c.Expr[Any](c.resetAllAttrs(t))
  }

  def createLens[T: c.WeakTypeTag](c: Context)(lensTpe: c.universe.Type, name: String) = {
    import c.universe._

    def abort(reason: String) = c.abort(c.enclosingPosition, reason)
    def mkParam(name: String, tpe: Type) =
      ValDef(Modifiers(Flag.PARAM), newTermName(name), TypeTree(tpe), EmptyTree)

    import treeBuild._

    val calledMember = lensTpe.member(newTermName(name)) orElse {
      abort("value %s is not a member of %s".format(name, lensTpe))
    }
    val memberTpe = calledMember.typeSignatureIn(lensTpe) match {
      case NullaryMethodType(tpe) => tpe
      case _                      => abort("member %s is not a field".format(name))
    }

    val constructor =
      DefDef(
        Modifiers(),
        nme.CONSTRUCTOR,
        List(),
        List(List()),
        TypeTree(),
        Block(
          List(Apply(Select(Super(This(""), ""), nme.CONSTRUCTOR), Nil)),
          Literal(Constant(()))))

    val getF =
      DefDef(
        Modifiers(), newTermName("get"), List(),
        List(List(mkParam("x$", lensTpe))),
        TypeTree(),
        Select(Ident(newTermName("x$")), newTermName(name))
      )

    val setF =
      DefDef(
        Modifiers(), newTermName("set"), List(),
        List(List(mkParam("x$", lensTpe), mkParam("v$", memberTpe))),
        TypeTree(),
        Apply(
          Select(Ident(newTermName("x$")), newTermName("copy")),
          List(AssignOrNamedArg(Ident(newTermName(name)), Ident(newTermName("v$"))))
        )
      )

    Block(
      List(
        ClassDef(Modifiers(Flag.FINAL), newTypeName("$anon"), List(),
          Template(List(
            AppliedTypeTree(
              Ident(c.mirror.staticClass("rillit.Lens")), List(TypeTree(lensTpe), TypeTree(memberTpe)))),
            emptyValDef, List(
              constructor,
              getF,
              setF
            ))
        )),
      Apply(Select(New(Ident(newTypeName("$anon"))), nme.CONSTRUCTOR), List())
    )
  }
}
