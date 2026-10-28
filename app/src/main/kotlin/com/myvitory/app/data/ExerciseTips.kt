package com.myvitory.app.data

/**
 * Pontos-chave de execução do catálogo inicial: o que conferir antes de cada série.
 * Ficam no código (não no banco) porque são conteúdo do app, não do usuário.
 */
object ExerciseTips {
    private val tips = mapOf(
        "supino_reto" to listOf(
            "Escápulas juntas e para baixo, pés firmes no chão.",
            "Desça a barra até a linha do peito com os cotovelos a uns 45° do tronco.",
            "Empurre sem tirar o quadril do banco.",
        ),
        "supino_inclinado_halteres" to listOf(
            "Banco entre 30° e 45°.",
            "Desça os halteres ao lado do peito, controlando a descida.",
            "Suba aproximando os halteres sem bater um no outro.",
        ),
        "crucifixo_maquina" to listOf(
            "Ajuste o banco para as mãos ficarem na altura do peito.",
            "Cotovelos levemente flexionados o movimento todo.",
            "Volte devagar até sentir o peito alongar, sem passar da linha do ombro.",
        ),
        "crossover" to listOf(
            "Um pé à frente e tronco levemente inclinado.",
            "Feche os braços em arco, como num abraço, com cotovelos fixos.",
            "Controle a volta; não deixe o peso puxar os braços para trás.",
        ),
        "flexao" to listOf(
            "Corpo reto da cabeça ao calcanhar: abdômen e glúteos contraídos.",
            "Mãos um pouco mais abertas que os ombros.",
            "Desça até o peito quase tocar o chão.",
        ),
        "triceps_polia" to listOf(
            "Cotovelos colados ao corpo e parados.",
            "Estenda até travar o braço, sem jogar o tronco.",
            "Suba só até o antebraço passar da linha do chão.",
        ),
        "triceps_frances" to listOf(
            "Cotovelos apontando para cima, perto da cabeça.",
            "Desça o halter atrás da cabeça com controle.",
            "Não arqueie a lombar: abdômen firme.",
        ),
        "mergulho_paralelas" to listOf(
            "Ombros longe das orelhas durante toda a série.",
            "Desça até o cotovelo chegar perto de 90°.",
            "Tronco mais reto foca no tríceps; inclinado, no peito.",
        ),
        "puxada_frente" to listOf(
            "Pegada um pouco mais aberta que os ombros.",
            "Puxe levando os cotovelos para baixo e para trás, até o queixo.",
            "Não balance o tronco para ajudar.",
        ),
        "remada_curvada" to listOf(
            "Tronco inclinado perto de 45°, coluna neutra.",
            "Puxe a barra em direção ao umbigo, cotovelos perto do corpo.",
            "Joelhos levemente flexionados e firmes.",
        ),
        "remada_baixa" to listOf(
            "Peito aberto e coluna reta, sem jogar o tronco para trás.",
            "Puxe o cabo até a barriga juntando as escápulas.",
            "Estenda os braços devagar na volta.",
        ),
        "remada_unilateral" to listOf(
            "Mão e joelho apoiados no banco, costas paralelas ao chão.",
            "Puxe o halter em direção ao quadril, não ao ombro.",
            "Não gire o tronco para subir mais peso.",
        ),
        "barra_fixa" to listOf(
            "Comece com os braços estendidos e ombros ativos.",
            "Suba até o queixo passar a barra, sem chutar as pernas.",
            "Desça controlando até estender de novo.",
        ),
        "rosca_direta" to listOf(
            "Cotovelos parados ao lado do corpo.",
            "Suba sem balançar o tronco.",
            "Desça até quase estender o braço.",
        ),
        "rosca_alternada" to listOf(
            "Gire o punho para cima enquanto sobe.",
            "Um braço por vez, sem mover o cotovelo.",
            "Desça devagar antes de começar o outro lado.",
        ),
        "rosca_martelo" to listOf(
            "Pegada neutra, palmas viradas uma para a outra.",
            "Cotovelos fixos ao lado do corpo.",
            "Suba até o antebraço passar da linha do chão.",
        ),
        "agachamento_livre" to listOf(
            "Pés na largura dos ombros, pontas levemente para fora.",
            "Desça com o quadril para trás e joelhos na direção dos pés.",
            "Coluna neutra e calcanhares no chão do começo ao fim.",
        ),
        "leg_press_45" to listOf(
            "Pés na largura do quadril, no meio da plataforma.",
            "Desça até o joelho chegar perto de 90°, sem tirar o quadril do encosto.",
            "Não trave o joelho no topo.",
        ),
        "agachamento_hack" to listOf(
            "Costas inteiras apoiadas no encosto.",
            "Desça até a coxa ficar paralela à plataforma.",
            "Empurre pelo meio do pé, sem tirar o calcanhar.",
        ),
        "agachamento_bulgaro" to listOf(
            "Pé de trás apoiado no banco, passo da frente longo.",
            "Desça na vertical até o joelho de trás quase tocar o chão.",
            "Tronco levemente inclinado para frente.",
        ),
        "cadeira_extensora" to listOf(
            "Joelho alinhado com o eixo da máquina.",
            "Estenda as pernas e segure um instante no topo.",
            "Desça devagar, sem deixar o peso bater.",
        ),
        "stiff" to listOf(
            "Joelhos levemente flexionados e parados.",
            "Desça a barra rente às pernas levando o quadril para trás.",
            "Pare quando sentir o posterior alongar, com a coluna ainda reta.",
        ),
        "mesa_flexora" to listOf(
            "Joelho alinhado com o eixo da máquina, quadril colado no banco.",
            "Flexione até o rolo chegar perto do glúteo.",
            "Volte devagar até quase estender.",
        ),
        "levantamento_terra" to listOf(
            "Barra rente à canela, coluna neutra antes de puxar.",
            "Empurre o chão com as pernas e estenda o quadril junto.",
            "Barra perto do corpo do começo ao fim.",
        ),
        "elevacao_pelvica" to listOf(
            "Parte de cima das costas apoiada no banco, barra sobre o quadril.",
            "Suba até o tronco ficar alinhado com as coxas.",
            "Contraia o glúteo no topo, sem arquear a lombar.",
        ),
        "cadeira_abdutora" to listOf(
            "Costas apoiadas e quadril no fundo do banco.",
            "Abra as pernas com controle e segure um instante.",
            "Feche devagar, sem deixar o peso bater.",
        ),
        "panturrilha_em_pe" to listOf(
            "Ponta dos pés na borda, calcanhares livres.",
            "Desça até alongar bem a panturrilha.",
            "Suba o mais alto possível e segure um segundo.",
        ),
        "panturrilha_sentado" to listOf(
            "Apoio sobre a coxa, perto do joelho.",
            "Desça o calcanhar até alongar bem.",
            "Suba até a ponta do pé e segure um segundo.",
        ),
        "panturrilha_leg_press" to listOf(
            "Só a ponta dos pés na borda de baixo da plataforma.",
            "Joelhos estendidos, mas sem travar.",
            "Empurre com a ponta dos pés e volte devagar.",
        ),
        "desenvolvimento_halteres" to listOf(
            "Halteres na linha das orelhas no começo.",
            "Empurre para cima sem arquear a lombar.",
            "Desça até o cotovelo ficar um pouco abaixo do ombro.",
        ),
        "elevacao_lateral" to listOf(
            "Cotovelos levemente flexionados.",
            "Suba os braços até a linha dos ombros, não acima.",
            "Sem balanço: se precisar, diminua o peso.",
        ),
        "crucifixo_invertido" to listOf(
            "Peito apoiado no encosto, braços à frente.",
            "Abra os braços para trás juntando as escápulas.",
            "Movimento curto e controlado, sem encolher os ombros.",
        ),
        "abdominal_polia" to listOf(
            "De joelhos, corda ao lado da cabeça.",
            "Enrole o tronco levando o peito em direção ao quadril.",
            "O quadril fica parado: quem trabalha é o abdômen.",
        ),
        "elevacao_pernas" to listOf(
            "Lombar colada no chão ou no apoio.",
            "Suba as pernas sem usar impulso.",
            "Desça devagar sem encostar os pés no chão.",
        ),
    )

    fun forExercise(id: String): List<String> = tips[id].orEmpty()
}
