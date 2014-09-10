package ru.nsu.fit.djachenko.evolution;

import javax.swing.*;

//class for managing window for GUI representation
public class Window extends JFrame
{
	private ModelingPanel panel;

	public Window(Drawer drawer)
	{
		super();

		initUI(drawer.getCurrentSpace());

		new Timer(Constants.DELAY_MILLIS, e->{
			synchronized (drawer)
			{
				update(drawer.getCurrentSpace());
			}
		}).start();
	}

	private void initUI(int[][] table)
	{
		panel = new ModelingPanel(table);

		setContentPane(panel);

		pack();
		setLocationRelativeTo(null);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
	}

	void update(int[][] displayModel)
	{
		panel.update(displayModel);
	}
}
