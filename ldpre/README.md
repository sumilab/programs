This program is an implementation of LDPRE in [COINS](https://sourceforge.net/projects/coins-project/) compiler.

Example of compilation
`java -classpath ./classes coins.driver.Driver -coins:target=x86_64,ssa-opt=esplt/divex/prun/ldpre/srd3`

* `esplt`: critical edge elimination
* `divex`: making each statement three-address code
* `prun`: converting program from normal form to SSA form
* `dce`: applying LDPRE
* `srd3`: converting program from SSA form to normal form

